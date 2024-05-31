package net.javaguides.departmentservice.entity;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import jakarta.persistence.Entity;
import jakarta.persistence.*;
import lombok.*;


import java.io.Serial;
import java.io.Serializable;

@Entity
@Table(name = "departments")
@Getter
@Setter
@EqualsAndHashCode
@ToString
@JsonPropertyOrder({"departmentId", "departmentName", "departmentDescription", "departmentCode"})
public class Department implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "department_id_generator")
    @SequenceGenerator(name = "department_id_generator", sequenceName = "department_id_seq", allocationSize = 1)
    private long departmentId;
    private String departmentName;
    private String departmentDescription;
    private String departmentCode;

    public Department() {
        //default constructor
    }

    public Department(long departmentId, String departmentName, String departmentDescription, String departmentCode) {
        this.departmentId = departmentId;
        this.departmentName = departmentName;
        this.departmentDescription = departmentDescription;
        this.departmentCode = departmentCode;
    }
}
