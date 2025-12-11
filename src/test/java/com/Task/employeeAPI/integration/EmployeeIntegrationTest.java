package com.Task.employeeAPI.integration;


import com.Task.employeeAPI.dao.Entity.Employee;
import com.Task.employeeAPI.dao.Enums.Role;
import com.Task.employeeAPI.dao.Repository.EmployeeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.test.context.support.WithMockUser;


import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Transactional
class EmployeeIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EmployeeRepository employeeRepository;

    private Employee existingEmployee;

    @BeforeEach
    void setup() {
        employeeRepository.deleteAll();
        existingEmployee = new Employee();
        existingEmployee.setName("Farid");
        existingEmployee.setSurname("Valiyev");
        existingEmployee.setEmail("farid@example.com");
        existingEmployee.setPassword("password123");
        existingEmployee.setRole(Role.EMPLOYEE);
        employeeRepository.save(existingEmployee);
    }


    @Test
    @WithMockUser(roles = "HR_MANAGER")
    @DisplayName("GET /employees should return all employees")
    void shouldReturnAllEmployees() throws Exception {
        mockMvc.perform(get("/employees")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].email", is("farid@example.com")));
    }


    @Test
    @WithMockUser(roles = {"HR"})
    @DisplayName("GET /employees/{id} should return the employee by ID")
    void shouldReturnEmployeeById() throws Exception {
        mockMvc.perform(get("/employees/{id}", existingEmployee.getId())
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Farid")))
                .andExpect(jsonPath("$.email", is("farid@example.com")));
    }

    @Test
    @DisplayName("POST /employees/signup should create a new employee")
    void shouldSignupNewEmployee() throws Exception {
        String newEmployeeJson = """
        {
          "name": "John",
          "surname": "Doe",
          "email": "john.doe@example.com",
          "password": "secure123",
          "role": "EMPLOYEE"
        }
    """;

        mockMvc.perform(post("/employees/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(newEmployeeJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email", is("john.doe@example.com")))
                .andExpect(jsonPath("$.user.role", is("EMPLOYEE")))
                .andExpect(jsonPath("$.token").exists());
    }


    @Test
    @WithMockUser(roles = {"HEAD_MANAGER"})
    @DisplayName("DELETE /employees/{id} should mark employee as deleted")
    void shouldDeleteEmployeeById() throws Exception {
        mockMvc.perform(delete("/employees/{id}", existingEmployee.getId()))
                .andDo(print())
                .andExpect(status().isOk());

        Employee deletedEmployee = employeeRepository.findById(existingEmployee.getId()).orElseThrow();
        assertTrue(deletedEmployee.isDeleted());
    }
}