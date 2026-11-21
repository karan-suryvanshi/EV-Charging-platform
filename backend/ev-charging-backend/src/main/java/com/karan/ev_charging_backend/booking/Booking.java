
package com.karan.ev_charging_backend.booking;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.karan.ev_charging_backend.auth.User;
import com.karan.ev_charging_backend.publicstation.PublicChargingStation;
import com.karan.ev_charging_backend.station.Station;
import org.hibernate.annotations.Check;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "bookings")
@Check(name = "ck_booking_single_station_source",
        constraints = "(station_id IS NULL) <> (public_station_id IS NULL)")
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "station_id")
    private Station station;

    @ManyToOne
    @JoinColumn(name = "public_station_id")
    private PublicChargingStation publicStation;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false)
    private String customerName;

    @Column(nullable = false)
    private LocalDate bookingDate;

    @Column(nullable = false)
    private LocalTime startTime;

    @Column(nullable = false)
    private LocalTime endTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookingStatus status = BookingStatus.REQUESTED;

    public Booking() {}

    public Long getId() { return id; }

    public Station getStation() { return station; }
    public void setStation(Station station) { this.station = station; }

    public PublicChargingStation getPublicStation() { return publicStation; }
    public void setPublicStation(PublicChargingStation publicStation) {
        this.publicStation = publicStation;
    }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public LocalDate getBookingDate() { return bookingDate; }
    public void setBookingDate(LocalDate bookingDate) {
        this.bookingDate = bookingDate;
    }

    public LocalTime getStartTime() { return startTime; }
    public void setStartTime(LocalTime startTime) {
        this.startTime = startTime;
    }

    public LocalTime getEndTime() { return endTime; }
    public void setEndTime(LocalTime endTime) {
        this.endTime = endTime;
    }

    public BookingStatus getStatus() { return status; }
    public void setStatus(BookingStatus status) {
        this.status = status;
    }
}
