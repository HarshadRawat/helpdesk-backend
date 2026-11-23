package com.example.helpdesk_backend.bootstrap;

import com.example.helpdesk_backend.entity.AuditLog;
import com.example.helpdesk_backend.entity.Comment;
import com.example.helpdesk_backend.entity.Ticket;
import com.example.helpdesk_backend.entity.User;
import com.example.helpdesk_backend.enums.Priority;
import com.example.helpdesk_backend.enums.Role;
import com.example.helpdesk_backend.enums.Status;
import com.example.helpdesk_backend.repository.AuditLogRepository;
import com.example.helpdesk_backend.repository.CommentRepository;
import com.example.helpdesk_backend.repository.TicketRepository;
import com.example.helpdesk_backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
public class AdminBootstrap {

    private final UserRepository userRepository;
    private final TicketRepository ticketRepository;
    private final CommentRepository commentRepository;
    private final AuditLogRepository auditLogRepository;
    private final PasswordEncoder passwordEncoder;
    private final boolean enabled;
    private final String adminName;
    private final String adminEmail;
    private final String adminPassword;

    public AdminBootstrap(
            UserRepository userRepository,
            TicketRepository ticketRepository,
            CommentRepository commentRepository,
            AuditLogRepository auditLogRepository,
            PasswordEncoder passwordEncoder,
            @Value("${app.bootstrap.admin.enabled:false}") boolean enabled,
            @Value("${app.bootstrap.admin.name:Admin}") String adminName,
            @Value("${app.bootstrap.admin.email:${APP_BOOTSTRAP_ADMIN_EMAIL:admin@localhost}}") String adminEmail,
            @Value("${app.bootstrap.admin.password:${APP_BOOTSTRAP_ADMIN_PASSWORD:ChangeMe123!}}") String adminPassword) {
        this.userRepository = userRepository;
        this.ticketRepository = ticketRepository;
        this.commentRepository = commentRepository;
        this.auditLogRepository = auditLogRepository;
        this.passwordEncoder = passwordEncoder;
        this.enabled = enabled;
        this.adminName = adminName;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void createDefaultAdminIfMissing() {
        if (!enabled) {
            return;
        }

        // 1. Primary Admin User
        User admin = userRepository.findByEmail(adminEmail.toLowerCase()).orElse(null);
        if (admin == null) {
            admin = new User();
            admin.setName(adminName);
            admin.setEmail(adminEmail.toLowerCase());
            admin.setPassword(passwordEncoder.encode(adminPassword));
            admin.setRole(Role.ADMIN);
            admin = userRepository.save(admin);
        }

        // 2. IT Support Technicians
        User support1 = userRepository.findByEmail("support@example.com").orElse(null);
        if (support1 == null) {
            support1 = new User();
            support1.setName("Alex Rivera");
            support1.setEmail("support@example.com");
            support1.setPassword(passwordEncoder.encode("Support@123"));
            support1.setRole(Role.AGENT);
            support1 = userRepository.save(support1);
        }

        User support2 = userRepository.findByEmail("marcus.vance@example.com").orElse(null);
        if (support2 == null) {
            support2 = new User();
            support2.setName("Marcus Vance");
            support2.setEmail("marcus.vance@example.com");
            support2.setPassword(passwordEncoder.encode("Support@123"));
            support2.setRole(Role.AGENT);
            support2 = userRepository.save(support2);
        }

        // 3. Employees
        User employee1 = userRepository.findByEmail("employee@example.com").orElse(null);
        if (employee1 == null) {
            employee1 = new User();
            employee1.setName("Sarah Connor");
            employee1.setEmail("employee@example.com");
            employee1.setPassword(passwordEncoder.encode("Employee@123"));
            employee1.setRole(Role.CUSTOMER);
            employee1 = userRepository.save(employee1);
        }

        User employee2 = userRepository.findByEmail("john.doe@example.com").orElse(null);
        if (employee2 == null) {
            employee2 = new User();
            employee2.setName("John Doe");
            employee2.setEmail("john.doe@example.com");
            employee2.setPassword(passwordEncoder.encode("Employee@123"));
            employee2.setRole(Role.CUSTOMER);
            employee2 = userRepository.save(employee2);
        }

        // 4. Seed Rich Initial Tickets, Comments & Audit Trail if empty
        if (ticketRepository.count() == 0) {
            Instant now = Instant.now();

            // Ticket 1: Open, High Priority (Sarah Connor)
            Ticket t1 = new Ticket();
            t1.setTitle("MacBook Pro M2 - Kernel panic on external 4K monitor connection");
            t1.setDescription("Whenever connecting to the Dell UltraSharp 4K via Thunderbolt 4, the macOS system crashes with an IOGPU panic report. Happens on both USB-C ports.");
            t1.setPriority(Priority.HIGH);
            t1.setStatus(Status.OPEN);
            t1.setCreatedBy(employee1);
            t1.setSlaDueAt(now.plus(4, ChronoUnit.HOURS));
            t1.setSlaBreached(false);
            t1 = ticketRepository.save(t1);

            // Ticket 2: In Progress, Urgent, with Comments (Sarah Connor & Alex Rivera)
            Ticket t2 = new Ticket();
            t2.setTitle("Corporate GlobalProtect VPN drops connection every 15 minutes");
            t2.setDescription("VPN gateway disconnects intermittently during remote work sessions. Error code: GW-TIMEOUT. Disrupts SSH terminals and database tunnel.");
            t2.setPriority(Priority.URGENT);
            t2.setStatus(Status.IN_PROGRESS);
            t2.setCreatedBy(employee1);
            t2.setAssignedTo(support1);
            t2.setSlaDueAt(now.plus(1, ChronoUnit.HOURS));
            t2.setSlaBreached(false);
            t2 = ticketRepository.save(t2);

            Comment c1 = new Comment();
            c1.setTicket(t2);
            c1.setUser(employee1);
            c1.setMessage("Attached crash timestamps: disconnected at 09:15, 09:30, and 09:45 AM.");
            c1.setInternal(false);
            commentRepository.save(c1);

            Comment c2 = new Comment();
            c2.setTicket(t2);
            c2.setUser(support1);
            c2.setMessage("Inspecting Palo Alto gateway MTU packets. We found packet fragmentation on WAN link.");
            c2.setInternal(true); // Internal technician note
            commentRepository.save(c2);

            // Ticket 3: Open, Medium (Sarah Connor)
            Ticket t3 = new Ticket();
            t3.setTitle("Software License: JetBrains IntelliJ IDEA Ultimate allocation");
            t3.setDescription("Requesting corporate IntelliJ IDEA license allocation for Spring Boot backend development. Approved by engineering manager.");
            t3.setPriority(Priority.MEDIUM);
            t3.setStatus(Status.OPEN);
            t3.setCreatedBy(employee1);
            t3.setSlaDueAt(now.plus(18, ChronoUnit.HOURS));
            t3.setSlaBreached(false);
            t3 = ticketRepository.save(t3);

            // Ticket 4: Resolved (Sarah Connor & Alex Rivera)
            Ticket t4 = new Ticket();
            t4.setTitle("AWS IAM Staging Read-Replica DB Credentials");
            t4.setDescription("Need read-only RDS credentials for the QA staging database to inspect incident replication logs.");
            t4.setPriority(Priority.LOW);
            t4.setStatus(Status.RESOLVED);
            t4.setCreatedBy(employee1);
            t4.setAssignedTo(support1);
            t4.setSlaDueAt(now.minus(2, ChronoUnit.HOURS));
            t4.setSlaBreached(false);
            t4.setResolvedAt(now.minus(45, ChronoUnit.MINUTES));
            t4 = ticketRepository.save(t4);

            // Ticket 5: SLA Breached, Urgent (John Doe & Marcus Vance)
            Ticket t5 = new Ticket();
            t5.setTitle("Emergency: Production Kafka cluster broker connectivity degradation");
            t5.setDescription("Cluster node 3 is reporting high I/O wait times, causing consumer group lag on the order processing topic.");
            t5.setPriority(Priority.URGENT);
            t5.setStatus(Status.IN_PROGRESS);
            t5.setCreatedBy(employee2);
            t5.setAssignedTo(support2);
            t5.setSlaDueAt(now.minus(2, ChronoUnit.HOURS));
            t5.setSlaBreached(true); // Marked as breached
            t5 = ticketRepository.save(t5);

            // Ticket 6: Closed (John Doe & Alex Rivera)
            Ticket t6 = new Ticket();
            t6.setTitle("Ergonomic chair and dual-monitor riser setup for Desk 402");
            t6.setDescription("Requesting Facilities assistance to mount dual Dell monitor arms on workstation 402.");
            t6.setPriority(Priority.LOW);
            t6.setStatus(Status.CLOSED);
            t6.setCreatedBy(employee2);
            t6.setAssignedTo(support1);
            t6.setSlaDueAt(now.minus(24, ChronoUnit.HOURS));
            t6.setSlaBreached(false);
            t6.setResolvedAt(now.minus(12, ChronoUnit.HOURS));
            t6 = ticketRepository.save(t6);

            // 5. Seed Audit Logs for Compliance Exploration
            AuditLog a1 = new AuditLog();
            a1.setAction("TICKET_CREATED");
            a1.setEntityType("ticket");
            a1.setEntityId(t1.getId());
            a1.setActorId(employee1.getId());
            a1.setOldValue(null);
            a1.setNewValue("{\"id\":" + t1.getId() + ",\"title\":\"" + t1.getTitle() + "\",\"status\":\"OPEN\",\"priority\":\"HIGH\"}");
            auditLogRepository.save(a1);

            AuditLog a2 = new AuditLog();
            a2.setAction("TICKET_ASSIGNED");
            a2.setEntityType("ticket");
            a2.setEntityId(t2.getId());
            a2.setActorId(admin.getId());
            a2.setOldValue("{\"assignedTo\":null}");
            a2.setNewValue("{\"assignedTo\":{\"id\":" + support1.getId() + ",\"name\":\"Alex Rivera\"}}");
            auditLogRepository.save(a2);

            AuditLog a3 = new AuditLog();
            a3.setAction("STATUS_CHANGED");
            a3.setEntityType("ticket");
            a3.setEntityId(t2.getId());
            a3.setActorId(support1.getId());
            a3.setOldValue("{\"status\":\"OPEN\"}");
            a3.setNewValue("{\"status\":\"IN_PROGRESS\"}");
            auditLogRepository.save(a3);

            AuditLog a4 = new AuditLog();
            a4.setAction("STATUS_CHANGED");
            a4.setEntityType("ticket");
            a4.setEntityId(t4.getId());
            a4.setActorId(support1.getId());
            a4.setOldValue("{\"status\":\"IN_PROGRESS\"}");
            a4.setNewValue("{\"status\":\"RESOLVED\",\"resolutionNotes\":\"Granted read-replica policy to staging IAM role.\"}");
            auditLogRepository.save(a4);

            AuditLog a5 = new AuditLog();
            a5.setAction("SLA_BREACH_ALERT");
            a5.setEntityType("ticket");
            a5.setEntityId(t5.getId());
            a5.setActorId(admin.getId());
            a5.setOldValue("{\"slaBreached\":false}");
            a5.setNewValue("{\"slaBreached\":true,\"alertDispatched\":true}");
            auditLogRepository.save(a5);
        }
    }
}
