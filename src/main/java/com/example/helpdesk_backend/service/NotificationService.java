package com.example.helpdesk_backend.service;

import com.example.helpdesk_backend.entity.Ticket;
import com.example.helpdesk_backend.entity.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final JavaMailSender mailSender;
    private final String frontendUrl;

    @Autowired
    public NotificationService(
            JavaMailSender mailSender,
            @Value("${app.frontend.url:http://localhost:8080}") String frontendUrl) {
        this.mailSender = mailSender;
        this.frontendUrl = frontendUrl;
    }

    public void sendTicketStatusNotification(Ticket ticket, User user, String oldStatus, String newStatus) {
        if (user == null || user.getEmail() == null || user.getEmail().isBlank()) {
            return;
        }

        String subject = "Ticket status updated - #" + ticket.getId();
        String message = "Hello " + user.getName() + ",\n\n"
                + "Your ticket '" + ticket.getTitle() + "' status changed from " + oldStatus + " to " + newStatus + ".\n"
                + "Ticket ID: " + ticket.getId() + "\n";

        sendEmail(user.getEmail(), subject, message);
    }

    public void sendTicketAssignmentNotification(Ticket ticket, User agent) {
        if (agent == null || agent.getEmail() == null || agent.getEmail().isBlank()) {
            return;
        }

        String subject = "New ticket assigned - #" + ticket.getId();
        String message = "Hello " + agent.getName() + ",\n\n"
                + "A new ticket has been assigned to you: '" + ticket.getTitle() + "'.\n"
                + "Ticket ID: " + ticket.getId();

        sendEmail(agent.getEmail(), subject, message);
    }

    @Async
    public void sendPasswordResetNotification(User user, String resetToken) {
        if (user == null || user.getEmail() == null || user.getEmail().isBlank()) {
            return;
        }

        String resetUrl = frontendUrl + "/reset-password?token=" + resetToken;
        String subject = "HelpDesk - Password Reset Request";
        String message = "Hello " + user.getName() + ",\n\n"
                + "A password reset request was received for your account.\n"
                + "You can reset your password using the link below:\n"
                + resetUrl + "\n\n"
                + "Reset token: " + resetToken + "\n\n"
                + "This link will expire in 15 minutes. If you did not request this, please ignore this email.";

        sendEmail(user.getEmail(), subject, message);
    }

    @Async
    public void sendSlaBreachAlert(Ticket ticket) {
        String adminEmail = "admin@localhost";
        String subject = "🚨 SLA Breach Alert - Ticket #" + ticket.getId();
        String message = "Hello Admin,\n\n"
                + "Ticket #" + ticket.getId() + " ('" + ticket.getTitle() + "') has breached its SLA resolution deadline.\n"
                + "Priority: " + ticket.getPriority() + "\n"
                + "Due At: " + ticket.getSlaDueAt() + "\n"
                + "Assigned Agent: " + (ticket.getAssignedTo() != null ? ticket.getAssignedTo().getName() : "Unassigned") + "\n\n"
                + "Please review and take escalation measures.";

        sendEmail(adminEmail, subject, message);
    }

    private void sendEmail(String to, String subject, String message) {
        try {
            SimpleMailMessage mail = new SimpleMailMessage();
            mail.setTo(to);
            mail.setSubject(subject);
            mail.setText(message);
            mailSender.send(mail);
            log.info("Email sent to {} for subject {}", to, subject);
        } catch (Exception ex) {
            log.warn("Email notification failed for {}: {}", to, ex.getMessage());
        }
    }
}
