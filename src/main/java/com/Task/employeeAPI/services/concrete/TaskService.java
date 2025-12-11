package com.Task.employeeAPI.services.concrete;

import com.Task.employeeAPI.dao.Entity.Employee;
import com.Task.employeeAPI.dao.Entity.Task;
import com.Task.employeeAPI.dao.Entity.TaskWorkflow;
import com.Task.employeeAPI.dao.Enums.Role;
import com.Task.employeeAPI.dao.Enums.Status;
import com.Task.employeeAPI.dao.Repository.EmployeeRepository;
import com.Task.employeeAPI.dao.Repository.TaskRepository;
import com.Task.employeeAPI.dao.Repository.TaskWorkflowRepository;
import com.Task.employeeAPI.dto.EmployeeDTO;
import com.Task.employeeAPI.dto.NotificationDTO;
import com.Task.employeeAPI.dto.TaskCreateDTO;
import com.Task.employeeAPI.dto.TaskDTO;
import com.Task.employeeAPI.exceptions.BadRequestException;
import com.Task.employeeAPI.exceptions.NotFoundException;
//import com.Task.employeeAPI.notification.NotificationProducer;
import com.Task.employeeAPI.notification.NotificationProducer;
import com.Task.employeeAPI.security.CustomUserDetails;
import com.Task.employeeAPI.services.abstraction.ITaskService;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TaskService implements ITaskService {

    private final TaskRepository taskRepository;
    private final EmployeeRepository employeeRepository;
    private final EmployeeService employeeService;
    private final TaskWorkflowRepository taskWorkflowRepository;
    private final ModelMapper modelMapper;
    private final EmailService emailService;
    private final NotificationProducer notificationProducer;


    @Override
    public TaskDTO createTask(TaskCreateDTO taskDTO) {
        if (taskDTO == null)
            throw new BadRequestException("Task input must not be null!");

        if (taskDTO.getEmployeeId() == null)
            throw new BadRequestException("Employee ID must not be null!");

        // Find employee to assign task to
        Employee employee = employeeRepository
                .findByIdAndIsDeletedFalse(taskDTO.getEmployeeId())
                .orElseThrow(() -> new NotFoundException(
                        "Employee with ID " + taskDTO.getEmployeeId() + " doesn't exist!"
                ));

        if (employee.getRole() == Role.HEAD_MANAGER) {
            throw new BadRequestException("Cannot assign tasks to HEAD_MANAGER.");
        }

        // Find logged-in user (updatedBy)
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Object principal = authentication.getPrincipal();

        String email;

        if (principal instanceof CustomUserDetails customUser) {
            email = customUser.getEmail();
        } else if (principal instanceof org.springframework.security.core.userdetails.User springUser) {
            email = springUser.getUsername();
        } else {
            throw new IllegalStateException("Unsupported principal type: " + principal.getClass());
        }

        Employee updatedBy = employeeRepository.findByEmailAndIsDeletedFalse(email);

        // 🔥 MANUAL MAPPING — NO MODELMAPPER FOR INPUT
        Task task = new Task();
        task.setTitle(taskDTO.getTitle());
        task.setDescription(taskDTO.getDescription());
        task.setPriority(taskDTO.getPriority());
        task.setEmployee(employee);
        task.setStatus(Status.CREATED);

        taskRepository.save(task);

        // Create workflow record
        TaskWorkflow taskWorkflow = new TaskWorkflow();
        taskWorkflow.setTask(task);
        taskWorkflow.setStatus(Status.CREATED);
        taskWorkflow.setLastUpdated(LocalDateTime.now());
        taskWorkflow.setUpdatedBy(updatedBy);
        taskWorkflowRepository.save(taskWorkflow);

//        // Send Kafka notification
//        notificationProducer.sendNotification(
//                new NotificationDTO(
//                        employee.getEmail(),
//                        "You were assigned with new task",
//                        task.getDescription()
//                )
//        );

        // 🔥 OUTPUT: use ModelMapper ONLY for response
        TaskDTO dto = modelMapper.map(task, TaskDTO.class);
        dto.setEmployee(modelMapper.map(employee, EmployeeDTO.class));

        return dto;
    }



    @Override
    @Transactional(readOnly = true)
    public TaskDTO findTaskById(Integer id) {
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Task with id " + id + " was not found!"));

        TaskDTO dto = modelMapper.map(task, TaskDTO.class);
        dto.setEmployee(modelMapper.map(task.getEmployee(), EmployeeDTO.class));
        return dto;
    }

    @Override
    @Transactional(readOnly = true)
    public List<TaskDTO> findAll() {
        return taskRepository.findAll()
                .stream()
                .map(task -> {
                    TaskDTO dto = modelMapper.map(task, TaskDTO.class);
                    if (task.getEmployee() != null) {
                        dto.setEmployee(modelMapper.map(task.getEmployee(), EmployeeDTO.class));
                    }
                    return dto;
                })
                .toList();
    }


    @Transactional(readOnly = true)
    public List<TaskDTO> findAllEmployeeTasks(Integer id) {
        return taskRepository.findByEmployee_id(id)
                .stream()
                .map(task -> {
                    TaskDTO dto = modelMapper.map(task, TaskDTO.class);
                    if (task.getEmployee() != null) {
                        dto.setEmployee(modelMapper.map(task.getEmployee(), EmployeeDTO.class));
                    }
                    return dto;
                })
                .toList();
    }


    @Override
    public TaskDTO deleteTaskById(Integer id) {
        Task task = taskRepository
                .findById(id)
                .orElseThrow(() -> new NotFoundException("Task with ID " + id + " doesn't exist!"));

        List<TaskWorkflow> workflows = taskWorkflowRepository.findByTask_Id(task.getId());
        taskWorkflowRepository.deleteAll(workflows);
        taskRepository.delete(task);
        return modelMapper.map(task, TaskDTO.class);
    }

    @Override
    public TaskDTO updateTaskById(Integer id, TaskDTO taskDTO) {

        if (taskDTO == null) {
            throw new BadRequestException("Task input must not be null");
        }

        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Task with id " + id + " was not found!"));

        // Employee stays the same — no need to re-query DB
        Employee employee = task.getEmployee();

        task.setTitle(taskDTO.getTitle());
        task.setDescription(taskDTO.getDescription());
        task.setPriority(taskDTO.getPriority());

        taskRepository.save(task);

//        notificationProducer.sendNotification(
//                new NotificationDTO(
//                        employee.getEmail(),
//                        "Task updated",
//                        "Task '" + task.getTitle() + "' was updated."
//                )
//        );

        TaskDTO dto = modelMapper.map(task, TaskDTO.class);
        dto.setEmployee(modelMapper.map(employee, EmployeeDTO.class));
        return dto;
    }

}
