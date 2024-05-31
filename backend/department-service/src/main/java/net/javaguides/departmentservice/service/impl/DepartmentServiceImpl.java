package net.javaguides.departmentservice.service.impl;

import lombok.AllArgsConstructor;
import net.javaguides.departmentservice.dto.DepartmentDto;
import net.javaguides.departmentservice.entity.Department;
import net.javaguides.departmentservice.repository.DepartmentRepository;
import net.javaguides.departmentservice.service.DepartmentService;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class DepartmentServiceImpl implements DepartmentService {

    private DepartmentRepository departmentRepository;

    @Override
    public DepartmentDto saveDepartment(DepartmentDto departmentDto) {

        // convert department dto to department jpa entity
        Department department = new Department(
                departmentDto.getId(),
                departmentDto.getDepartmentName(),
                departmentDto.getDepartmentDescription(),
                departmentDto.getDepartmentCode()
        );

        Department savedDepartment = departmentRepository.save(department);

        return new DepartmentDto(
                savedDepartment.getDepartmentId(),
                savedDepartment.getDepartmentName(),
                savedDepartment.getDepartmentDescription(),
                savedDepartment.getDepartmentCode()
        );
    }
    @Override
    public DepartmentDto getDepartmentByCode(String departmentCode) {
        // Retrieve department from the repository
        Department department = departmentRepository.findByDepartmentCode(departmentCode);

        // Check if department exists
        if (department != null) {
            // Create and return DepartmentDto
            return new DepartmentDto(
                    department.getDepartmentId(),
                    department.getDepartmentName(),
                    department.getDepartmentDescription(),
                    department.getDepartmentCode()
            );
        } else {
            // Department with given code not found, handle appropriately
            throw new DepartmentNotFoundException("Department with code " + departmentCode + " not found");
        }
    }
}
