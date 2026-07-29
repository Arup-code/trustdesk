// Types mirror the real backend/ai-service response shapes, confirmed against:
// - backend/src/main/java/app/dexcode/trustdesk/entities/Ticket.java (queue list, plain camelCase)
// - backend/src/main/java/app/dexcode/trustdesk/dto/TicketDetailResponse.java (plain camelCase)
// - backend/src/main/java/app/dexcode/trustdesk/dto/TriageResponse.java (@JsonProperty snake_case)
// - backend/src/main/java/app/dexcode/trustdesk/dto/DraftResponse.java (@JsonProperty snake_case)
// - backend/src/main/java/app/dexcode/trustdesk/entities/ToolActionRequest.java (plain camelCase)
// - backend/src/main/java/app/dexcode/trustdesk/entities/EvalRun.java (plain camelCase)

export interface Ticket {
  ticketId: string;
  customerId?: string;
  orderId?: string;
  channel?: string;
  subject: string;
  body: string;
  status?: string;
  category?: string | null;
  priority?: string | null;
  sentiment?: string | null;
  shouldEscalate?: boolean | null;
  reasonSummary?: string | null;
}

export interface CustomerSummary {
  customerId: string;
  name: string;
  email: string;
  tier: string;
  country: string;
  verified: boolean;
}

export interface OrderSummary {
  orderId: string;
  status: string;
  trackingNumber: string;
  eligibleReturnUntil: string | null;
}

export interface TicketDetail {
  ticketId: string;
  subject: string;
  body: string;
  channel: string;
  status: string;
  category: string | null;
  priority: string | null;
  sentiment: string | null;
  shouldEscalate: boolean | null;
  reasonSummary: string | null;
  customer: CustomerSummary | null;
  order: OrderSummary | null;
}

export interface TriageResponse {
  category: string;
  priority: string;
  sentiment: string;
  should_escalate: boolean;
  reason_summary: string;
  guardrail_flagged: boolean;
  guardrail_category: string | null;
}

export interface RecommendedAction {
  tool_name: string;
  requires_human_approval: boolean;
  reason: string;
}

export interface DraftResponse {
  body: string;
  citations: string[];
  recommended_actions: RecommendedAction[];
  status: string;
  retrieved_doc_ids: string[];
  guardrail_flagged: boolean;
  guardrail_category: string | null;
}

export interface ToolActionRequest {
  actionId: string;
  ticketId: string;
  toolName: string;
  status: string;
  requiresHumanApproval: boolean;
  riskLevel: string;
  result?: Record<string, unknown> | null;
}

export interface EvalRun {
  evalRunId: string;
  startedAt: string;
  completedAt: string;
  totalCases: number;
  metrics: Record<string, number>;
  caseResults: Record<string, unknown>[];
}
