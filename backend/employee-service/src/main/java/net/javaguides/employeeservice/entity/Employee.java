package net.javaguides.employeeservice.entity;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serial;
import java.io.Serializable;

@Entity
@Table(name = "employees")
@Getter
@Setter
@EqualsAndHashCode
@ToString
@JsonPropertyOrder({"employeeId", "firstName", "lastName", "email", "departmentCode"})
public class Employee implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "employee_id_generator")
    @SequenceGenerator(name = "employee_id_generator", sequenceName = "employee_id_seq", allocationSize = 0)
    private long employeeId;
    private String firstName;
    private String lastName;
    @Column(unique = true)
    private String email;
    @Column(name = "department_code")
    private String departmentCode;

    public Employee() {
        //default constructor
    }

    public Employee(long employeeId, String firstName, String lastName, String email) {
        this.employeeId = employeeId;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
    }

    public Employee(long employeeId, String firstName, String lastName, String email, String departmentCode) {
        this.employeeId = employeeId;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.departmentCode = departmentCode;
    }
}

