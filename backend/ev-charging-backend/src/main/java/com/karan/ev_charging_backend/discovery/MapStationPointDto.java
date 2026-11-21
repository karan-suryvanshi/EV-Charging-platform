package com.karan.ev_charging_backend.discovery;

import java.math.BigDecimal;

public class MapStationPointDto {

    private String id;
    private Long internalId;
    private StationSource source;
    private String name;
    private String operator;
    private String location;
    private Integer sourceRecordId;
    private Integer sourcePage;
    private Integer sourcePdfRecordId;
    private String importBatchId;
    private BigDecimal ratingKw;
    private Integer connectorCount;
    private Double latitude;
    private Double longitude;
    private String chargerType;
    private Double pricePerUnit;
    private Boolean available;
    private boolean bookingEnabled;
    private boolean bookable;

    public MapStationPointDto() {
    }

    public MapStationPointDto(
            String id,
            Long internalId,
            StationSource source,
            String name,
            String operator,
            String location,
            Integer sourceRecordId,
            Integer sourcePage,
            Integer sourcePdfRecordId,
            String importBatchId,
            BigDecimal ratingKw,
            Integer connectorCount,
            Double latitude,
            Double longitude,
            String chargerType,
            Double pricePerUnit,
            Boolean available,
            boolean bookable) {
        this.id = id;
        this.internalId = internalId;
        this.source = source;
        this.name = name;
        this.operator = operator;
        this.location = location;
        this.sourceRecordId = sourceRecordId;
        this.sourcePage = sourcePage;
        this.sourcePdfRecordId = sourcePdfRecordId;
        this.importBatchId = importBatchId;
        this.ratingKw = ratingKw;
        this.connectorCount = connectorCount;
        this.latitude = latitude;
        this.longitude = longitude;
        this.chargerType = chargerType;
        this.pricePerUnit = pricePerUnit;
        this.available = available;
        this.bookable = bookable;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Long getInternalId() {
        return internalId;
    }

    public void setInternalId(Long internalId) {
        this.internalId = internalId;
    }

    public StationSource getSource() {
        return source;
    }

    public void setSource(StationSource source) {
        this.source = source;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public Integer getSourceRecordId() {
        return sourceRecordId;
    }

    public void setSourceRecordId(Integer sourceRecordId) {
        this.sourceRecordId = sourceRecordId;
    }

    public Integer getSourcePage() {
        return sourcePage;
    }

    public void setSourcePage(Integer sourcePage) {
        this.sourcePage = sourcePage;
    }

    public Integer getSourcePdfRecordId() {
        return sourcePdfRecordId;
    }

    public void setSourcePdfRecordId(Integer sourcePdfRecordId) {
        this.sourcePdfRecordId = sourcePdfRecordId;
    }

    public String getImportBatchId() {
        return importBatchId;
    }

    public void setImportBatchId(String importBatchId) {
        this.importBatchId = importBatchId;
    }

    public BigDecimal getRatingKw() {
        return ratingKw;
    }

    public void setRatingKw(BigDecimal ratingKw) {
        this.ratingKw = ratingKw;
    }

    public Integer getConnectorCount() {
        return connectorCount;
    }

    public void setConnectorCount(Integer connectorCount) {
        this.connectorCount = connectorCount;
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    public String getChargerType() {
        return chargerType;
    }

    public void setChargerType(String chargerType) {
        this.chargerType = chargerType;
    }

    public Double getPricePerUnit() {
        return pricePerUnit;
    }

    public void setPricePerUnit(Double pricePerUnit) {
        this.pricePerUnit = pricePerUnit;
    }

    public Boolean getAvailable() {
        return available;
    }

    public void setAvailable(Boolean available) {
        this.available = available;
    }

    public boolean isBookable() {
        return bookable;
    }

    public boolean isBookingEnabled() {
        return bookingEnabled;
    }

    public void setBookingEnabled(boolean bookingEnabled) {
        this.bookingEnabled = bookingEnabled;
    }

    public void setBookable(boolean bookable) {
        this.bookable = bookable;
    }
}
