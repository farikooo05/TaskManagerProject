package com.Task.employeeAPI.integration;


import com.Task.employeeAPI.dao.Entity.Employee;
import com.Task.employeeAPI.dao.Enums.Role;
import com.Task.employeeAPI.dao.Repository.EmployeeRepository;
import com.Task.employeeAPI.dao.Repository.TaskRepository;
import com.Task.employeeAPI.notification.NotificationProducer;
import com.Task.employeeAPI.services.concrete.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TaskIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private TaskRepository taskRepository;

    @MockitoBean
    private NotificationProducer notificationProducer;

    @MockitoBean
    private EmailService emailService;

    private Employee employee;

    @BeforeEach
    void setup() {
        taskRepository.deleteAll();
        employeeRepository.deleteAll();

        employee = new Employee();
        employee.setName("Farid");
        employee.setSurname("Valiyev");
        employee.setEmail("farid@example.com");
        employee.setPassword("12345");
        employee.setRole(Role.EMPLOYEE);

        employeeRepository.save(employee);
    }

    // ------------------------------------------------------
    // CREATE TASK
    // ------------------------------------------------------
    @Test
    @WithMockUser(roles = {"HEAD_MANAGER"})
    @DisplayName("POST /tasks should create a new task")
    void shouldCreateTask() throws Exception {

        String requestJson = """
            {
              "title": "Prepare",
              "description": "Prepare report",
              "priority": "HIGH",
              "employeeId": %d
            }
        """.formatted(employee.getId());

        mockMvc.perform(post("/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description", is("Prepare report")))
                .andExpect(jsonPath("$.employee.id", is(employee.getId())));
    }

    // ------------------------------------------------------
    // GET ALL TASKS
    // ------------------------------------------------------
    @Test
    @WithMockUser(roles = {"HEAD_MANAGER", "HR_MANAGER"})
    @DisplayName("GET /tasks should return all tasks")
    void shouldReturnAllTasks() throws Exception {

        String req = """
            {
              "title": "TaskTitle",
              "description": "Task 1",
              "priority": "LOW",
              "employeeId": %d
            }
        """.formatted(employee.getId());

        mockMvc.perform(post("/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(req))
                .andExpect(status().isOk());

        mockMvc.perform(get("/tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    // ------------------------------------------------------
    // GET BY ID
    // ------------------------------------------------------
    @Test
    @WithMockUser(roles = {"HEAD_MANAGER", "HR_MANAGER"})
    @DisplayName("GET /tasks/{id} should return a task by ID")
    void shouldReturnTaskById() throws Exception {

        String req = """
            {
              "title": "ReviewTitle",
              "description": "Review project",
              "priority": "MEDIUM",
              "employeeId": %d
            }
        """.formatted(employee.getId());

        String response = mockMvc.perform(post("/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(req))
                .andReturn()
                .getResponse()
                .getContentAsString();

        int taskId = com.jayway.jsonpath.JsonPath.read(response, "$.id");

        mockMvc.perform(get("/tasks/{id}", taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description", is("Review project")));
    }

    // ------------------------------------------------------
    // DELETE TASK
    // ------------------------------------------------------
    @Test
    @WithMockUser(roles = "HEAD_MANAGER")
    @DisplayName("DELETE /tasks/{id} should delete a task")
    void shouldDeleteTask() throws Exception {

        String req = """
            {
              "title": "TempTitle",
              "description": "Temporary task",
              "priority": "LOW",
              "employeeId": %d
            }
        """.formatted(employee.getId());

        String response = mockMvc.perform(post("/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(req))
                .andReturn()
                .getResponse()
                .getContentAsString();

        int taskId = com.jayway.jsonpath.JsonPath.read(response, "$.id");

        mockMvc.perform(delete("/tasks/{id}", taskId))
                .andExpect(status().isOk());

        boolean exists = taskRepository.existsById(taskId);
        assertFalse(exists);
    }
}