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
    private final String alertEmail;

    @Autowired
    public NotificationService(
            JavaMailSender mailSender,
            @Value("${app.frontend.url:http://localhost:8080}") String frontendUrl,
            @Value("${app.sla.alert-email:${app.bootstrap.admin.email:admin@localhost}}") String alertEmail) {
        this.mailSender = mailSender;
        this.frontendUrl = frontendUrl;
        this.alertEmail = alertEmail;
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
    public void sendSlaBreachDigestAlert(java.util.List<Ticket> breachedTickets) {
        if (breachedTickets == null || breachedTickets.isEmpty() || alertEmail == null || alertEmail.isBlank()) {
            return;
        }
        String subject = "🚨 SLA Breach Digest Alert - " + breachedTickets.size() + " ticket(s) overdue";
        StringBuilder sb = new StringBuilder();
        sb.append("Hello Admin,\n\n");
        sb.append("The following ").append(breachedTickets.size()).append(" ticket(s) have breached their SLA resolution deadlines:\n\n");
        for (Ticket ticket : breachedTickets) {
            sb.append("- Ticket #").append(ticket.getId())
                    .append(" [Priority: ").append(ticket.getPriority()).append("] ")
                    .append(": '").append(ticket.getTitle()).append("'\n");
        }
        sb.append("\nPlease review these tickets promptly in the HelpDesk portal.\n");
        sendEmail(alertEmail, subject, sb.toString());
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
