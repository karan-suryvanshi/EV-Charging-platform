
package com.karan.ev_charging_backend.publicstation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "public_charging_stations")
public class PublicChargingStation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_record_id")
    private Integer sourceRecordId;

    @Column(name = "source_page")
    private Integer sourcePage;

    @Column(name = "cpo_name")
    private String cpoName;

    private String ownership;
    private String state;
    private String district;

    @Column(name = "city_village")
    private String cityVillage;

    @Column(columnDefinition = "TEXT")
    private String location;

    @Column(precision = 12, scale = 6)
    private BigDecimal latitude;

    @Column(precision = 12, scale = 6)
    private BigDecimal longitude;

    @Column(name = "charger_type")
    private String chargerType;

    @Column(name = "rating_kw")
    private BigDecimal ratingKw;

    @Column(name = "secondary_rating_kw")
    private String secondaryRatingKw;

    @Column(name = "connector_count")
    private Integer connectorCount;

    @Column(name = "import_batch_id")
    private String importBatchId;

    @Column(name = "source_pdf_record_id")
    private Integer sourcePdfRecordId;

    @Column(name = "source_warnings", columnDefinition = "LONGTEXT")
    private String sourceWarnings;

    @Column(name = "booking_enabled", nullable = false, columnDefinition = "bit(1) default b'1'")
    private boolean bookingEnabled = true;

    public PublicChargingStation() {
    }

    public Long getId() {
        return id;
    }

    public Integer getSourceRecordId() {
        return sourceRecordId;
    }

    public Integer getSourcePage() {
        return sourcePage;
    }

    public String getCpoName() {
        return cpoName;
    }

    public String getOwnership() {
        return ownership;
    }

    public String getState() {
        return state;
    }

    public String getDistrict() {
        return district;
    }

    public String getCityVillage() {
        return cityVillage;
    }

    public String getLocation() {
        return location;
    }

    public BigDecimal getLatitude() {
        return latitude;
    }

    public BigDecimal getLongitude() {
        return longitude;
    }

    public String getChargerType() {
        return chargerType;
    }

    public BigDecimal getRatingKw() {
        return ratingKw;
    }

    public String getSecondaryRatingKw() {
        return secondaryRatingKw;
    }

    public Integer getConnectorCount() {
        return connectorCount;
    }

    public String getImportBatchId() {
        return importBatchId;
    }

    public Integer getSourcePdfRecordId() {
        return sourcePdfRecordId;
    }

    public String getSourceWarnings() {
        return sourceWarnings;
    }

    public boolean isBookingEnabled() {
        return bookingEnabled;
    }

    public void setBookingEnabled(boolean bookingEnabled) {
        this.bookingEnabled = bookingEnabled;
    }

    public void setCpoName(String cpoName) {
        this.cpoName = cpoName;
    }

    public void setOwnership(String ownership) {
        this.ownership = ownership;
    }

    public void setState(String state) {
        this.state = state;
    }

    public void setDistrict(String district) {
        this.district = district;
    }

    public void setCityVillage(String cityVillage) {
        this.cityVillage = cityVillage;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public void setLatitude(BigDecimal latitude) {
        this.latitude = latitude;
    }

    public void setLongitude(BigDecimal longitude) {
        this.longitude = longitude;
    }

    public void setChargerType(String chargerType) {
        this.chargerType = chargerType;
    }

    public void setRatingKw(BigDecimal ratingKw) {
        this.ratingKw = ratingKw;
    }

    public void setSecondaryRatingKw(String secondaryRatingKw) {
        this.secondaryRatingKw = secondaryRatingKw;
    }

    public void setConnectorCount(Integer connectorCount) {
        this.connectorCount = connectorCount;
    }
}
