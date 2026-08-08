package app.dexcode.trustdesk.services;

import app.dexcode.trustdesk.dto.AdminResetSummary;
import app.dexcode.trustdesk.entities.Ticket;
import app.dexcode.trustdesk.repositories.AgentRunTraceRepository;
import app.dexcode.trustdesk.repositories.ApprovalRepository;
import app.dexcode.trustdesk.repositories.DraftReplyRepository;
import app.dexcode.trustdesk.repositories.EvalRunRepository;
import app.dexcode.trustdesk.repositories.TicketRepository;
import app.dexcode.trustdesk.repositories.ToolActionRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AdminService {

    private final TicketRepository ticketRepository;
    private final ToolActionRequestRepository toolActionRequestRepository;
    private final ApprovalRepository approvalRepository;
    private final DraftReplyRepository draftReplyRepository;
    private final AgentRunTraceRepository agentRunTraceRepository;
    private final EvalRunRepository evalRunRepository;

    public AdminService(
        TicketRepository ticketRepository,
        ToolActionRequestRepository toolActionRequestRepository,
        ApprovalRepository approvalRepository,
        DraftReplyRepository draftReplyRepository,
        AgentRunTraceRepository agentRunTraceRepository,
        EvalRunRepository evalRunRepository
    ) {
        this.ticketRepository = ticketRepository;
        this.toolActionRequestRepository = toolActionRequestRepository;
        this.approvalRepository = approvalRepository;
        this.draftReplyRepository = draftReplyRepository;
        this.agentRunTraceRepository = agentRunTraceRepository;
        this.evalRunRepository = evalRunRepository;
    }

    @Transactional
    public AdminResetSummary resetDemoData() {
        int toolActionsDeleted = (int) toolActionRequestRepository.count();
        toolActionRequestRepository.deleteAll();

        int approvalsDeleted = (int) approvalRepository.count();
        approvalRepository.deleteAll();

        int draftRepliesDeleted = (int) draftReplyRepository.count();
        draftReplyRepository.deleteAll();

        int agentRunTracesDeleted = (int) agentRunTraceRepository.count();
        agentRunTraceRepository.deleteAll();

        int evalRunsDeleted = (int) evalRunRepository.count();
        evalRunRepository.deleteAll();

        List<Ticket> tickets = ticketRepository.findAll();
        for (Ticket ticket : tickets) {
            ticket.setCategory((String) null);
            ticket.setPriority((String) null);
            ticket.setSentiment((String) null);
            ticket.setShouldEscalate(null);
            ticket.setReasonSummary(null);
        }
        ticketRepository.saveAll(tickets);

        return new AdminResetSummary(
            tickets.size(), toolActionsDeleted, approvalsDeleted,
            draftRepliesDeleted, agentRunTracesDeleted, evalRunsDeleted);
    }
}
