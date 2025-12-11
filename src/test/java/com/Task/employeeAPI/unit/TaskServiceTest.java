package com.Task.employeeAPI.unit;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.Task.employeeAPI.dao.Entity.Employee;
import com.Task.employeeAPI.dao.Entity.Task;
import com.Task.employeeAPI.dao.Entity.TaskWorkflow;
import com.Task.employeeAPI.dao.Enums.Priority;
import com.Task.employeeAPI.dao.Repository.EmployeeRepository;
import com.Task.employeeAPI.dao.Repository.TaskRepository;
import com.Task.employeeAPI.dao.Repository.TaskWorkflowRepository;
import com.Task.employeeAPI.dto.EmployeeDTO;
import com.Task.employeeAPI.dto.NotificationDTO;
import com.Task.employeeAPI.dto.TaskCreateDTO;
import com.Task.employeeAPI.dto.TaskDTO;
import com.Task.employeeAPI.exceptions.BadRequestException;
import com.Task.employeeAPI.exceptions.NotFoundException;
import com.Task.employeeAPI.notification.NotificationProducer;
import com.Task.employeeAPI.security.CustomUserDetails;
import com.Task.employeeAPI.services.concrete.EmailService;
import com.Task.employeeAPI.services.concrete.EmployeeService;
import com.Task.employeeAPI.services.concrete.TaskService;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @InjectMocks
    private TaskService taskService;

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private EmployeeService employeeService;

    @Mock
    private TaskWorkflowRepository taskWorkflowRepository;

    @Mock
    private ModelMapper modelMapper;

    @Mock
    private EmailService emailService;

    @Mock
    private NotificationProducer notificationProducer; // ← This is the red one

    @BeforeEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    // Helper to simulate authenticated user
    private void setAuth(String email, int userId) {
        CustomUserDetails user = new CustomUserDetails(
                userId, "u", "p", email,
                List.of(), true
        );
        SecurityContextHolder.getContext()
                .setAuthentication(new TestingAuthenticationToken(user, null));
    }

    @Test
    void createTask_nullDto_throwsBadRequest() {
        assertThrows(BadRequestException.class, () -> taskService.createTask(null));
    }

    @Test
    void createTask_noEmployeeId_throwsBadRequest() {
        TaskCreateDTO dto = new TaskCreateDTO();
        assertThrows(BadRequestException.class, () -> taskService.createTask(dto));
    }

    @Test
    void createTask_employeeNotFound_throwsNotFound() {
        TaskCreateDTO dto = new TaskCreateDTO();
        dto.setEmployeeId(5);

        // EmployeeRepository is used, not EmployeeService
        when(employeeRepository.findByIdAndIsDeletedFalse(5))
                .thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> taskService.createTask(dto));
    }


    @Test
    void createTask_validDto_savesTaskAndWorkflowAndReturnsDto() {
        // Arrange
        TaskCreateDTO in = new TaskCreateDTO();
        in.setEmployeeId(7);
        in.setTitle("Task A");
        in.setDescription("foo");
        in.setPriority(Priority.MEDIUM);

        // assigned employee exists
        Employee empEntity = new Employee();
        empEntity.setId(7);
        empEntity.setEmail("emp@example.com");

        when(employeeRepository.findByIdAndIsDeletedFalse(7))
                .thenReturn(Optional.of(empEntity));

        // authenticated updater
        setAuth("updater@example.com", 99);

        Employee updater = new Employee();
        updater.setEmail("updater@example.com");

        when(employeeRepository.findByEmailAndIsDeletedFalse("updater@example.com"))
                .thenReturn(updater);

        // repository save returns same task
        when(taskRepository.save(any(Task.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        // workflow save
        when(taskWorkflowRepository.save(any(TaskWorkflow.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        // notification
        doNothing().when(notificationProducer)
                .sendNotification(any(NotificationDTO.class));

        // modelMapper output
        TaskDTO out = new TaskDTO();
        when(modelMapper.map(any(Task.class), eq(TaskDTO.class)))
                .thenReturn(out);
        when(modelMapper.map(eq(empEntity), eq(EmployeeDTO.class)))
                .thenReturn(new EmployeeDTO());

        // Act
        TaskDTO result = taskService.createTask(in);

        // Assert
        assertSame(out, result);                        // returned expected DTO
        verify(taskRepository).save(any(Task.class));   // task saved
        verify(taskWorkflowRepository).save(any(TaskWorkflow.class)); // workflow saved
        verify(notificationProducer).sendNotification(any(NotificationDTO.class)); // kafka sent
    }





    @Test
    void findTaskById_notFound_throwsNotFound() {
        when(taskRepository.findById(1)).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> taskService.findTaskById(1));
    }

    @Test
    void findTaskById_found_returnsDto() {
        Task t = new Task();
        when(taskRepository.findById(2))
                .thenReturn(Optional.of(t));
        TaskDTO dto = new TaskDTO();
        when(modelMapper.map(t, TaskDTO.class)).thenReturn(dto);
        assertSame(dto, taskService.findTaskById(2));
    }

    @Test
    void findAll_returnsListOfDto() {
        // Arrange
        Task t1 = new Task();
        Task t2 = new Task();

        when(taskRepository.findAll())
                .thenReturn(List.of(t1, t2));

        TaskDTO d1 = new TaskDTO();
        TaskDTO d2 = new TaskDTO();

        when(modelMapper.map(t1, TaskDTO.class)).thenReturn(d1);
        when(modelMapper.map(t2, TaskDTO.class)).thenReturn(d2);

        // Act
        List<TaskDTO> list = taskService.findAll();

        // Assert
        assertEquals(2, list.size());
        assertTrue(list.containsAll(List.of(d1, d2)));
    }

    @Test
    void findAllEmployeeTasks_returnsOnlyThatEmployeeTasks() {
        // Arrange
        Employee e = new Employee();
        e.setId(10);

        Task t = new Task();
        t.setEmployee(e);

        when(taskRepository.findByEmployee_id(10))
                .thenReturn(List.of(t));

        TaskDTO mapped = new TaskDTO();
        EmployeeDTO mappedEmp = new EmployeeDTO();

        when(modelMapper.map(t, TaskDTO.class))
                .thenReturn(mapped);

        when(modelMapper.map(e, EmployeeDTO.class))
                .thenReturn(mappedEmp);

        // Act
        List<TaskDTO> result = taskService.findAllEmployeeTasks(10);

        // Assert
        assertEquals(1, result.size());
        assertSame(mapped, result.get(0));
        assertSame(mappedEmp, result.get(0).getEmployee());

        verify(taskRepository).findByEmployee_id(10);
    }


    @Test
    void deleteTaskById_notFound_throwsNotFound() {
        when(taskRepository.findById(5)).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> taskService.deleteTaskById(5));
    }

    @Test
    void deleteTaskById_found_deletesTaskAndWorkflowsAndReturnsDto() {
        Task t = new Task();
        t.setId(6);

        // Mock: task exists
        when(taskRepository.findById(6))
                .thenReturn(Optional.of(t));

        // Mock: workflows exist
        List<TaskWorkflow> workflows = List.of(new TaskWorkflow(), new TaskWorkflow());
        when(taskWorkflowRepository.findByTask_Id(6))
                .thenReturn(workflows);

        // Map to DTO
        TaskDTO mapped = new TaskDTO();
        when(modelMapper.map(t, TaskDTO.class))
                .thenReturn(mapped);

        // Act
        TaskDTO out = taskService.deleteTaskById(6);

        // Assert
        assertSame(mapped, out);

        verify(taskWorkflowRepository).findByTask_Id(6);
        verify(taskWorkflowRepository).deleteAll(workflows);
        verify(taskRepository).delete(t);
    }


    @Test
    void updateTaskById_nullDto_throwsBadRequest() {
        assertThrows(BadRequestException.class, () -> taskService.updateTaskById(1, null));
    }

    @Test
    void updateTaskById_notFound_throwsNotFound() {
        TaskDTO in = new TaskDTO();
        when(taskRepository.findById(2))
                .thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> taskService.updateTaskById(2, in));
    }

    @Test
    void updateTaskById_success_updatesFieldsAndReturnsDto() {
        // existing task
        Task existing = new Task();
        Employee oldEmployee = new Employee();
        oldEmployee.setId(10);
        oldEmployee.setEmail("emp@mail.com");
        existing.setEmployee(oldEmployee);

        when(taskRepository.findById(3))
                .thenReturn(Optional.of(existing));

        // input DTO
        TaskDTO input = new TaskDTO();
        input.setTitle("new title");
        input.setDescription("new desc");
        input.setPriority(Priority.HIGH);

        // output mapping
        TaskDTO mapped = new TaskDTO();
        when(modelMapper.map(existing, TaskDTO.class)).thenReturn(mapped);
        when(modelMapper.map(oldEmployee, EmployeeDTO.class)).thenReturn(new EmployeeDTO());

        // Act
        TaskDTO out = taskService.updateTaskById(3, input);

        // Assert
        assertSame(mapped, out);
        assertEquals("new title", existing.getTitle());
        assertEquals("new desc", existing.getDescription());
        assertEquals(Priority.HIGH, existing.getPriority());
        assertSame(oldEmployee, existing.getEmployee()); // employee does not change

        verify(taskRepository).save(existing);
        verify(notificationProducer).sendNotification(any(NotificationDTO.class));
    }


}