package com.example.helpdesk_backend.event;

import com.example.helpdesk_backend.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class TicketEventListener {

    private static final Logger log = LoggerFactory.getLogger(TicketEventListener.class);

    private final NotificationService notificationService;

    public TicketEventListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Async("taskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onTicketStatusChanged(TicketStatusChangedEvent event) {
        log.info("Handling asynchronous status change event for ticket #{}", event.ticket().getId());
        notificationService.sendTicketStatusNotification(
                event.ticket(),
                event.recipient(),
                event.oldStatus(),
                event.newStatus()
        );
    }

    @Async("taskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onTicketAssigned(TicketAssignedEvent event) {
        log.info("Handling asynchronous assignment event for ticket #{}", event.ticket().getId());
        notificationService.sendTicketAssignmentNotification(
                event.ticket(),
                event.agent()
        );
    }
}
