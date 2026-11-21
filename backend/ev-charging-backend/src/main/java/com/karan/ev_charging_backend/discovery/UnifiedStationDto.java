package com.karan.ev_charging_backend.discovery;

import java.math.BigDecimal;

public class UnifiedStationDto {

    private String id;
    private Long internalId;
    private StationSource source;
    private String name;
    private String operator;
    private String cpoName;
    private String ownership;
    private String location;
    private String state;
    private String district;
    private String cityVillage;
    private Integer sourceRecordId;
    private Integer sourcePage;
    private Integer sourcePdfRecordId;
    private String importBatchId;
    private Double latitude;
    private Double longitude;
    private String chargerType;
    private Double pricePerUnit;
    private BigDecimal ratingKw;
    private String secondaryRatingKw;
    private Integer connectorCount;
    private Boolean available;
    private boolean bookingEnabled;
    private boolean bookable;
    private String bookingRestrictionReason;
    private double averageRating;
    private int reviewCount;

    public UnifiedStationDto() {
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

    public String getCpoName() {
        return cpoName;
    }

    public void setCpoName(String cpoName) {
        this.cpoName = cpoName;
    }

    public String getOwnership() {
        return ownership;
    }

    public void setOwnership(String ownership) {
        this.ownership = ownership;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getDistrict() {
        return district;
    }

    public void setDistrict(String district) {
        this.district = district;
    }

    public String getCityVillage() {
        return cityVillage;
    }

    public void setCityVillage(String cityVillage) {
        this.cityVillage = cityVillage;
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

    public BigDecimal getRatingKw() {
        return ratingKw;
    }

    public void setRatingKw(BigDecimal ratingKw) {
        this.ratingKw = ratingKw;
    }

    public String getSecondaryRatingKw() {
        return secondaryRatingKw;
    }

    public void setSecondaryRatingKw(String secondaryRatingKw) {
        this.secondaryRatingKw = secondaryRatingKw;
    }

    public Integer getConnectorCount() {
        return connectorCount;
    }

    public void setConnectorCount(Integer connectorCount) {
        this.connectorCount = connectorCount;
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

    public String getBookingRestrictionReason() {
        return bookingRestrictionReason;
    }

    public void setBookingRestrictionReason(String bookingRestrictionReason) {
        this.bookingRestrictionReason = bookingRestrictionReason;
    }

    public double getAverageRating() {
        return averageRating;
    }

    public void setAverageRating(double averageRating) {
        this.averageRating = averageRating;
    }

    public int getReviewCount() {
        return reviewCount;
    }

    public void setReviewCount(int reviewCount) {
        this.reviewCount = reviewCount;
    }
}
