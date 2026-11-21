$ErrorActionPreference = 'Stop'

$backendRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path

if (-not $env:JAVA_HOME) {
    Write-Error 'Set JAVA_HOME to an installed JDK 17 before starting the backend.'
    exit 2
}

$javaExe = Join-Path $env:JAVA_HOME 'bin\java.exe'
if (-not (Test-Path -LiteralPath $javaExe)) {
    Write-Error 'JAVA_HOME does not point to a JDK installation.'
    exit 2
}
$javaVersion = (& $javaExe -version 2>&1 | Out-String)
$majorVersion = 0
if ($javaVersion -match 'version "(\d+)') {
    $majorVersion = [int]$matches[1]
}
if ($majorVersion -lt 17) {
    Write-Error "This project requires Java 17 or higher. Found Java $majorVersion in JAVA_HOME."
    exit 2
}
$env:Path = "$(Join-Path $env:JAVA_HOME 'bin');$env:Path"

$dbUsername = Read-Host 'Local MySQL username for ev_charging_db'
if ([string]::IsNullOrWhiteSpace($dbUsername) -or $dbUsername -eq '${DB_USERNAME}') {
    Write-Error 'Enter the actual local MySQL username; a placeholder is not accepted.'
    exit 2
}

$dbPasswordSecure = Read-Host 'Local MySQL password' -AsSecureString
$passwordPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($dbPasswordSecure)
try {
    $dbPassword = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($passwordPointer)
}
finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($passwordPointer)
    $dbPasswordSecure.Dispose()
}
if ([string]::IsNullOrEmpty($dbPassword)) {
    Write-Error 'A local MySQL password is required.'
    exit 2
}

$jwtSecure = Read-Host 'JWT signing secret (use the current value to keep existing tokens valid)' -AsSecureString
$jwtPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($jwtSecure)
try {
    $jwtSecret = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($jwtPointer)
}
finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($jwtPointer)
    $jwtSecure.Dispose()
}
if ([string]::IsNullOrWhiteSpace($jwtSecret)) {
    Write-Error 'A JWT signing secret is required.'
    exit 2
}

$exitCode = 1
Push-Location $backendRoot
try {
    $env:DB_URL = 'jdbc:mysql://localhost:3306/ev_charging_db'
    $env:DB_USERNAME = $dbUsername
    $env:DB_PASSWORD = $dbPassword
    $env:JWT_SECRET = $jwtSecret
    Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue

    $mysql = Get-Command mysql.exe -ErrorAction SilentlyContinue
    if ($mysql) {
        $env:MYSQL_PWD = $dbPassword
        & $mysql.Source --protocol=TCP --host=127.0.0.1 --port=3306 `
            --user=$dbUsername --database=ev_charging_db `
            --batch --skip-column-names -e 'SELECT 1'
        $connectionCheck = $LASTEXITCODE
        Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue
        if ($connectionCheck -ne 0) {
            throw 'Read-only MySQL login check failed. No database changes were made.'
        }
        Write-Host 'Read-only MySQL login check passed.'
    }
    else {
        Write-Warning 'mysql.exe was not found; Spring Boot will validate the connection at startup.'
    }

    & .\mvnw.cmd spring-boot:run
    $exitCode = $LASTEXITCODE
}
catch {
    Write-Error $_
    $exitCode = 1
}
finally {
    Remove-Item Env:DB_USERNAME,Env:DB_PASSWORD,Env:DB_URL,Env:JWT_SECRET,Env:MYSQL_PWD `
        -ErrorAction SilentlyContinue
    $dbPassword = $null
    $jwtSecret = $null
    Pop-Location
}

exit $exitCode
