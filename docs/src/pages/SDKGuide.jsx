import React, { useState, useRef, useEffect } from 'react';
import CodeBlock from '../components/CodeBlock';
import { motion } from 'framer-motion';

/* ─── Code snippets (kept as named consts for parser compatibility) ─ */

const CODE_ENABLE_NOTIFY = [
  '@Configuration',
  '@EnableNotify(basePackage = "com.notify.ecommerce")',
  'public class NotifyConfig {',
  '    // No additional beans needed — the SDK auto-wires everything.',
  '}',
].join('\n');

const CODE_EVENT = [
  '@Event(',
  '    key              = "ORDER_PLACED",',
  '    description      = "Customer placed an order",',
  '    eventType        = "static",',
  '    scheduleIntent   = "immediate",',
  '    preferredTimeWindow = "",',
  '    priority         = 5,',
  '    payload          = OrderPayload.class',
  ')',
  'public OrderPayload placeOrder(OrderPayload payload) {',
  '    // Business logic — return value is captured as the event payload',
  '    orders.save(payload);',
  '    return payload;',
  '}',
  '',
  '// ── Deferred example ─────────────────────────────────────────────',
  '@Event(',
  '    key              = "ABANDONED_CART",',
  '    description      = "Customer abandoned their shopping cart",',
  '    eventType        = "deferred",',
  '    scheduleIntent   = "deferred",',
  '    preferredTimeWindow = "09:00-21:00",',
  '    priority         = 3,',
  '    payload          = CartPayload.class',
  ')',
  'public CartPayload abandonCart(CartPayload payload) {',
  '    carts.save(payload);',
  '    return payload;',
  '}',
].join('\n');

const CODE_SUBJECT_SUPPLIER = [
  '// Email recipient for ORDER_PLACED',
  '@SubjectSupplier(event = "ORDER_PLACED", description = "Resolves order customer to email recipients")',
  'public List<Subject> getOrderPlacedSubjects(OrderPayload payload) {',
  '    Customer c = customers.get(payload.getCustomerId());',
  '    if (c == null) return List.of();',
  '    return List.of(new EmailSubject(',
  '        c.getId(), c.getEmail(), null, null, null,',
  '        Map.of("firstName", c.getName())',
  '    ));',
  '}',
  '',
  '// SMS recipient for urgent events',
  '@SubjectSupplier(event = "PAYMENT_FAILED", description = "Resolves customer to SMS for urgent payment alerts")',
  'public List<Subject> getPaymentFailedSubjects(OrderPayload payload) {',
  '    Customer c = customers.get(payload.getCustomerId());',
  '    if (c == null) return List.of();',
  '    return List.of(new SmsSubject(',
  '        c.getId(), c.getPhone(), null,',
  '        Map.of("firstName", c.getName())',
  '    ));',
  '}',
  '',
  '// Multi-channel: both SMS and Email',
  '@SubjectSupplier(event = "SUSPICIOUS_LOGIN", description = "Resolves account holder to SMS + email for security alerts")',
  'public List<Subject> getSuspiciousLoginSubjects(LoginPayload payload) {',
  '    Account acc = accounts.get(payload.getUserId());',
  '    if (acc == null) return List.of();',
  '    return List.of(',
  '        new SmsSubject(acc.getId(), acc.getPhone(), null, Map.of("holderName", acc.getHolderName())),',
  '        new EmailSubject(acc.getId(), acc.getEmail(), null, null, null, Map.of("holderName", acc.getHolderName()))',
  '    );',
  '}',
].join('\n');

const CODE_VOCABULARY_SUPPLIER = [
  '@VocabularySupplier(',
  '    event       = "ORDER_PLACED",',
  '    description = "Enriches order payload with customer name and default shipping address"',
  ')',
  'public OrderPayload orderPlacedVocabulary(OrderPayload payload) {',
  '    Customer c = customers.get(payload.getCustomerId());',
  '    if (c != null && (payload.getShippingAddress() == null || payload.getShippingAddress().isEmpty())) {',
  '        payload.setShippingAddress("Default address for " + c.getName());',
  '    }',
  '    return payload; // enriched payload is forwarded to the agents',
  '}',
  '',
  '// Banking example — join sender & receiver names before dispatch',
  '@VocabularySupplier(',
  '    event       = "LARGE_TRANSFER",',
  '    description = "Enriches transfer payload with sender and receiver names"',
  ')',
  'public TransactionPayload transferVocabulary(TransactionPayload payload) {',
  '    Account sender   = accounts.get(payload.getFromAccountId());',
  '    Account receiver = accounts.get(payload.getToAccountId());',
  '    log.info("Enriching transfer: {} -> {}",',
  '        sender   != null ? sender.getHolderName()   : "unknown",',
  '        receiver != null ? receiver.getHolderName() : "unknown");',
  '    return payload;',
  '}',
].join('\n');

const CODE_RULE = [
  '// Block high-value orders that may be fraudulent',
  '@Rule(name = "fraud-check", event = "ORDER_PLACED", description = "Blocks orders over $1000 as potential fraud")',
  'public boolean fraudCheck(OrderPayload payload) {',
  '    return payload.getAmount() < 1000.0; // false -> notification suppressed',
  '}',
  '',
  '// Only notify if all items are in stock',
  '@Rule(name = "inventory-check", event = "ORDER_PLACED", description = "Checks whether all items are in stock")',
  'public boolean inventoryCheck(OrderPayload payload) {',
  '    return inventoryService.allInStock(payload.getItems());',
  '}',
  '',
  '// Banking - compound velocity guard',
  '@Rule(name = "velocity-check", event = "LARGE_TRANSFER", description = "Blocks if sender has more than 5 recent transactions")',
  'public boolean velocityCheckRule(TransactionPayload payload) {',
  '    long recentCount = transactions.countByFromAccount(payload.getFromAccountId());',
  '    return recentCount < 5;',
  '}',
].join('\n');

const CODE_CALLBACK = [
  '// BEFORE hook',
  '@Callback(event = "ORDER_PLACED", when = Callback.When.BEFORE)',
  'public void beforeOrderPlaced(OrderPayload payload) {',
  '    log.info("[BEFORE] About to process ORDER_PLACED for order: {}", payload.getOrderId());',
  '    // e.g. pre-flight validation, locking, metrics start timer',
  '}',
  '',
  '// AFTER hook',
  '@Callback(event = "ORDER_PLACED", when = Callback.When.AFTER)',
  'public void afterOrderPlaced(OrderPayload payload) {',
  '    log.info("[AFTER] ORDER_PLACED processing complete for order: {}", payload.getOrderId());',
  '    // e.g. audit log, cache invalidation, analytics event',
  '}',
  '',
  '// Security alert example',
  '@Callback(event = "SUSPICIOUS_LOGIN", when = Callback.When.BEFORE)',
  'public void beforeSuspiciousLogin(LoginPayload payload) {',
  '    log.warn("[BEFORE] Security alert for user {} from {}", payload.getUserId(), payload.getLocation());',
  '}',
  '',
  '@Callback(event = "LARGE_TRANSFER", when = Callback.When.AFTER)',
  'public void afterLargeTransfer(TransactionPayload payload) {',
  '    log.info("[AFTER] Audit trail recorded for transfer {}, amount={} {}",',
  '        payload.getTransactionId(), payload.getAmount(), payload.getCurrency());',
  '}',
].join('\n');

const CODE_MODEL = [
  '@Model(description = "Payload for order placement and payment events")',
  'public class OrderPayload {',
  '',
  '    @Vocabulary(name = "orderId",         description = "Unique order identifier")',
  '    private String orderId;',
  '',
  '    @Vocabulary(name = "customerId",      description = "Customer who placed the order")',
  '    private String customerId;',
  '',
  '    @Vocabulary(name = "amount",          description = "Total order amount in USD")',
  '    private double amount;',
  '',
  '    @Vocabulary(name = "items",           description = "List of item names in the order")',
  '    private List<String> items;',
  '',
  '    @Vocabulary(name = "shippingAddress", description = "Delivery address for the order")',
  '    private String shippingAddress;',
  '',
  '    // standard getters / setters ...',
  '}',
].join('\n');

const CODE_VOCABULARY = [
  '@Model(description = "Payload for fund transfer events")',
  'public class TransactionPayload {',
  '',
  '    @Vocabulary(name = "transactionId",  description = "Unique transaction identifier")',
  '    private String transactionId;',
  '',
  '    @Vocabulary(name = "fromAccountId",  description = "Source account for the transfer")',
  '    private String fromAccountId;',
  '',
  '    @Vocabulary(name = "toAccountId",    description = "Destination account for the transfer")',
  '    private String toAccountId;',
  '',
  '    @Vocabulary(name = "amount",         description = "Transfer amount")',
  '    private double amount;',
  '',
  '    @Vocabulary(name = "currency",       description = "Currency code (e.g. USD, EUR)")',
  '    private String currency;',
  '',
  '    @Vocabulary(name = "type",           description = "Transaction type (WIRE, ACH, INTERNAL)")',
  '    private String type;',
  '}',
].join('\n');

const CODE_SCHEDULE = [
  '// Immediate fire',
  '@Event(key = "ORDER_PLACED", eventType = "static", scheduleIntent = "immediate", priority = 5, payload = OrderPayload.class)',
  '@NotificationSchedule(kind = NotificationSchedule.Kind.IMMEDIATE)',
  'public OrderPayload placeOrder(OrderPayload payload) {',
  '    return payload;',
  '}',
  '',
  '// Delayed with repeat',
  '@Event(key = "ABANDONED_CART", eventType = "deferred", scheduleIntent = "deferred", priority = 3, payload = CartPayload.class)',
  '@NotificationSchedule(',
  '    kind               = NotificationSchedule.Kind.DELAY,',
  '    startTime          = "2025-01-01T09:00:00",',
  '    repeatCount        = 3,',
  '    repeatInterval     = 24,',
  '    repeatIntervalUnit = "HOUR"',
  ')',
  'public CartPayload abandonCart(CartPayload payload) {',
  '    return payload;',
  '}',
  '',
  '// Windowed CRON-like dispatch (Mon-Fri, 9am-6pm, every hour)',
  '@NotificationSchedule(',
  '    kind               = NotificationSchedule.Kind.CRON,',
  '    daysOfWeek         = {1, 2, 3, 4, 5},',
  '    startTimeOfDay     = "09:00",',
  '    endTimeOfDay       = "18:00",',
  '    repeatInterval     = 1,',
  '    repeatIntervalUnit = "HOUR"',
  ')',
  '@Event(key = "DAILY_BALANCE_SUMMARY", eventType = "deferred", scheduleIntent = "deferred", priority = 2, payload = BalanceSummaryPayload.class)',
  'public BalanceSummaryPayload generateDailySummary(BalanceSummaryPayload payload) {',
  '    return payload;',
  '}',
].join('\n');

const CODE_MANAGED_CONFIG = [
  '@Service',
  'public class OrchestratorService {',
  '',
  '    // Sourced from the database config_entries table',
  '    @ManagedConfiguration(key = "agent.orchestrator.core-pool-size", source = ManagedConfiguration.ConfigSource.DB)',
  '    private int corePoolSize = 4;',
  '',
  '    // Sourced from a Kubernetes ConfigMap / @RefreshScope',
  '    @ManagedConfiguration(key = "agent.max-retries", source = ManagedConfiguration.ConfigSource.CONFIG_MAP)',
  '    private int maxRetries = 3;',
  '',
  '    // Default source (DB)',
  '    @ManagedConfiguration(key = "agent.dispatch.timeout-ms")',
  '    private long dispatchTimeoutMs = 5000L;',
  '',
  '    // These fields are updated automatically by ManagedConfigService',
  '    // when a Kafka config-change event or @RefreshScope refresh occurs.',
  '    // No restart required.',
  '}',
].join('\n');

const MAVEN_CODE = [
  '<dependency>',
  '    <groupId>com.notify.agent</groupId>',
  '    <artifactId>vocabulary-agent-client</artifactId>',
  '    <version>1.0.0</version>',
  '</dependency>',
].join('\n');

/* ─── Annotation metadata ─────────────────────────────────────── */

const ANNOTATIONS = [
  {
    id: 'enable-notify',
    name: '@EnableNotify',
    target: 'Class',
    emoji: '⚡',
    tagline: 'Bootstrap the SDK',
    description:
      'Place this on a Spring @Configuration class to activate the Notify.ai SDK. It scans the given base package and registers all annotation processors for @Event, @Rule, @Callback, @Vocabulary, @Model, @VocabularySupplier, and @SubjectSupplier.',
    attributes: [
      { name: 'basePackage', type: 'String', dflt: '""', desc: 'Root package to scan for Notify annotations. Falls back to notify.base-package in application.yml.' },
    ],
    code: CODE_ENABLE_NOTIFY,
  },
  {
    id: 'event',
    name: '@Event',
    target: 'Method',
    emoji: '🎯',
    tagline: 'Declare an event',
    description:
      'Intercepts a method and wraps its return value as a typed event payload. The SDK serialises it and pushes it asynchronously to the ACP server, which fans it out to the AI agent pipeline.',
    attributes: [
      { name: 'key',                type: 'String',   dflt: '',          desc: 'Unique event identifier (e.g. "ORDER_PLACED").' },
      { name: 'description',        type: 'String',   dflt: '""',        desc: 'Human-readable description shown to agents.' },
      { name: 'eventType',          type: 'String',   dflt: '',          desc: '"static" for immediate or "deferred" for scheduled dispatch.' },
      { name: 'scheduleIntent',     type: 'String',   dflt: '',          desc: '"immediate" or "deferred".' },
      { name: 'preferredTimeWindow',type: 'String',   dflt: '""',        desc: 'Time window hint for deferred events, e.g. "09:00-21:00".' },
      { name: 'priority',           type: 'int',      dflt: '',          desc: 'Dispatch priority (higher = processed first).' },
      { name: 'version',            type: 'String',   dflt: '"v1"',      desc: 'Schema version string.' },
      { name: 'payload',            type: 'Class<?>',  dflt: 'Void.class', desc: 'Explicit payload type override (defaults to the method return type).' },
    ],
    code: CODE_EVENT,
  },
  {
    id: 'subject-supplier',
    name: '@SubjectSupplier',
    target: 'Method',
    emoji: '👤',
    tagline: 'Resolve recipients',
    description:
      'Marks a method that returns the List<Subject> (recipients) for a specific event key. The SDK calls this method when preparing a notification dispatch. Use EmailSubject or SmsSubject to target recipients by channel.',
    attributes: [
      { name: 'event',       type: 'String', dflt: '',   desc: 'Event key this supplier is bound to.' },
      { name: 'description', type: 'String', dflt: '""', desc: 'Human-readable description.' },
    ],
    code: CODE_SUBJECT_SUPPLIER,
  },
  {
    id: 'vocabulary-supplier',
    name: '@VocabularySupplier',
    target: 'Method',
    emoji: '📚',
    tagline: 'Enrich event context',
    description:
      'Identifies a method that enriches the event payload before it is serialised and handed to the AI agents. Use this to join data from other services, add computed fields, or attach contextual metadata that improves template generation.',
    attributes: [
      { name: 'event',       type: 'String', dflt: '',   desc: 'Event key this supplier enriches.' },
      { name: 'description', type: 'String', dflt: '""', desc: 'Human-readable description.' },
    ],
    code: CODE_VOCABULARY_SUPPLIER,
  },
  {
    id: 'rule',
    name: '@Rule',
    target: 'Method',
    emoji: '📏',
    tagline: 'Gate notifications with business logic',
    description:
      'Marks a boolean-returning method as a business rule guard for an event. Returning false suppresses the notification. Describe rules in plain English — the AI agent translates them into fast, compiled expressions for low overhead.',
    attributes: [
      { name: 'name',        type: 'String', dflt: '',   desc: 'Unique rule identifier.' },
      { name: 'description', type: 'String', dflt: '""', desc: 'Natural language description of the rule.' },
      { name: 'event',       type: 'String', dflt: '""', desc: 'Event key this rule guards. Empty = global.' },
    ],
    code: CODE_RULE,
  },
  {
    id: 'callback',
    name: '@Callback',
    target: 'Method',
    emoji: '🔔',
    tagline: 'Hook into the event lifecycle',
    description:
      'Registers a method to run before or after an event is processed. Use BEFORE callbacks for pre-flight validation / logging and AFTER callbacks for audit trails, cache invalidation, or side-effects that must happen post-dispatch.',
    attributes: [
      { name: 'event', type: 'String',        dflt: '',  desc: 'Event key to hook into.' },
      { name: 'when',  type: 'Callback.When', dflt: '',  desc: 'BEFORE or AFTER the event is processed.' },
    ],
    code: CODE_CALLBACK,
  },
  {
    id: 'model',
    name: '@Model',
    target: 'Class',
    emoji: '🗂️',
    tagline: 'Describe a payload class',
    description:
      'A class-level marker indicating that the class is a typed event payload. All fields should be annotated with @Vocabulary so the AI agents can understand their semantics. The engine uses @Model classes to build a runtime vocabulary registry.',
    attributes: [
      { name: 'description', type: 'String', dflt: '""', desc: 'Optional human-readable description of the model purpose.' },
    ],
    code: CODE_MODEL,
  },
  {
    id: 'vocabulary',
    name: '@Vocabulary',
    target: 'Field',
    emoji: '🏷️',
    tagline: 'Annotate payload fields',
    description:
      "Field-level annotation used inside @Model classes. The name and description tell the AI agents what each field means, enabling accurate template generation and semantic reasoning over event data.",
    attributes: [
      { name: 'name',        type: 'String', dflt: '""', desc: 'Logical name of the field (used in templates).' },
      { name: 'description', type: 'String', dflt: '""', desc: "Natural-language description of the field's meaning." },
    ],
    code: CODE_VOCABULARY,
  },
  {
    id: 'notification-schedule',
    name: '@NotificationSchedule',
    target: 'Method / Class',
    emoji: '🕐',
    tagline: 'Configure dispatch timing',
    description:
      'Declares the scheduling strategy for a notification. Can be placed on a method alongside @Event or on a class for a default schedule. Supports immediate fire, a fixed delay, or cron-like windowed repeat patterns.',
    attributes: [
      { name: 'kind',               type: 'Kind',    dflt: '',         desc: 'IMMEDIATE, DELAY, or CRON.' },
      { name: 'eventEnabled',       type: 'boolean', dflt: 'true',     desc: 'Whether scheduling is active.' },
      { name: 'startTime',          type: 'String',  dflt: '""',       desc: 'ISO-8601 start timestamp for delayed sends.' },
      { name: 'repeatCount',        type: 'int',     dflt: '-1',       desc: 'Number of repetitions (-1 = indefinite).' },
      { name: 'repeatInterval',     type: 'int',     dflt: '1',        desc: 'Interval value between repetitions.' },
      { name: 'repeatIntervalUnit', type: 'String',  dflt: '"MINUTE"', desc: 'Time unit: MINUTE, HOUR, DAY, etc.' },
      { name: 'daysOfWeek',         type: 'int[]',   dflt: '{}',       desc: 'Days to restrict dispatch (1=MON ... 7=SUN).' },
      { name: 'startTimeOfDay',     type: 'String',  dflt: '""',       desc: 'Earliest time-of-day for dispatch (HH:mm).' },
      { name: 'endTimeOfDay',       type: 'String',  dflt: '""',       desc: 'Latest time-of-day for dispatch (HH:mm).' },
    ],
    code: CODE_SCHEDULE,
  },
  {
    id: 'managed-configuration',
    name: '@ManagedConfiguration',
    target: 'Field',
    emoji: '⚙️',
    tagline: 'Hot-reload config values',
    description:
      'Marks a field as dynamically reconfigurable at runtime. The ManagedConfigService discovers annotated fields via reflection and updates them whenever a config change arrives from a database config_entries table or via a Kubernetes ConfigMap / Spring @RefreshScope.',
    attributes: [
      { name: 'key',    type: 'String',       dflt: '',   desc: 'Dot-notation config key, e.g. "agent.orchestrator.core-pool-size".' },
      { name: 'source', type: 'ConfigSource', dflt: 'DB', desc: 'DB (config_entries table) or CONFIG_MAP (K8s / @RefreshScope).' },
    ],
    code: CODE_MANAGED_CONFIG,
  },
];

/* ─── Sub-components ──────────────────────────────────────────── */

const TAG_COLORS = {
  Method:            { bg: 'rgba(96,165,250,0.12)',  border: 'rgba(96,165,250,0.3)',  text: '#60a5fa' },
  Class:             { bg: 'rgba(167,139,250,0.12)', border: 'rgba(167,139,250,0.3)', text: '#a78bfa' },
  Field:             { bg: 'rgba(74,222,128,0.12)',  border: 'rgba(74,222,128,0.3)',  text: '#4ade80' },
  'Method / Class':  { bg: 'rgba(251,146,60,0.12)',  border: 'rgba(251,146,60,0.3)',  text: '#fb923c' },
};

const TargetTag = ({ target }) => {
  const c = TAG_COLORS[target] || TAG_COLORS.Method;
  return (
    <span style={{
      padding: '0.2rem 0.6rem', borderRadius: '999px',
      fontSize: '0.72rem', fontWeight: 700,
      backgroundColor: c.bg, border: `1px solid ${c.border}`, color: c.text,
    }}>
      @{target}
    </span>
  );
};

const AttrTable = ({ attrs }) => (
  <div style={{ overflowX: 'auto', marginBottom: '1.5rem' }}>
    <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.85rem' }}>
      <thead>
        <tr style={{ borderBottom: '1px solid rgba(255,255,255,0.08)' }}>
          {['Attribute', 'Type', 'Default', 'Description'].map((h) => (
            <th key={h} style={{ padding: '0.6rem 1rem', textAlign: 'left', color: '#64748b', fontWeight: 600, whiteSpace: 'nowrap' }}>{h}</th>
          ))}
        </tr>
      </thead>
      <tbody>
        {attrs.map((a, i) => (
          <tr key={a.name} style={{ borderBottom: '1px solid rgba(255,255,255,0.04)', backgroundColor: i % 2 === 0 ? 'transparent' : 'rgba(255,255,255,0.015)' }}>
            <td style={{ padding: '0.6rem 1rem', fontFamily: 'monospace', color: '#facc15', whiteSpace: 'nowrap' }}>{a.name}</td>
            <td style={{ padding: '0.6rem 1rem', fontFamily: 'monospace', color: '#94a3b8', whiteSpace: 'nowrap' }}>{a.type}</td>
            <td style={{ padding: '0.6rem 1rem', fontFamily: 'monospace', color: '#4ade80', whiteSpace: 'nowrap' }}>{a.dflt || '—'}</td>
            <td style={{ padding: '0.6rem 1rem', color: '#94a3b8', lineHeight: 1.5 }}>{a.desc}</td>
          </tr>
        ))}
      </tbody>
    </table>
  </div>
);

const AnnotationSection = ({ ann, isFirst }) => (
  <motion.section
    id={ann.id}
    initial={{ opacity: 0, y: 24 }}
    whileInView={{ opacity: 1, y: 0 }}
    viewport={{ once: true, margin: '-80px' }}
    transition={{ duration: 0.45 }}
    style={{
      marginBottom: '5rem',
      paddingTop: isFirst ? 0 : '1rem',
      borderTop: isFirst ? 'none' : '1px solid rgba(255,255,255,0.06)',
    }}
  >
    {/* Header */}
    <div style={{ display: 'flex', alignItems: 'flex-start', gap: '1rem', marginBottom: '1.25rem', flexWrap: 'wrap' }}>
      <span style={{ fontSize: '2rem', lineHeight: 1 }}>{ann.emoji}</span>
      <div style={{ flex: 1, minWidth: 0 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', flexWrap: 'wrap', marginBottom: '0.35rem' }}>
          <h2 style={{ fontSize: '1.6rem', fontWeight: 800, fontFamily: 'monospace', color: '#f8fafc', margin: 0 }}>
            {ann.name}
          </h2>
          <TargetTag target={ann.target} />
        </div>
        <p style={{ color: '#64748b', fontSize: '0.9rem', margin: 0, fontStyle: 'italic' }}>{ann.tagline}</p>
      </div>
    </div>

    {/* Description */}
    <p style={{ color: '#94a3b8', lineHeight: 1.8, marginBottom: '1.5rem', fontSize: '1rem' }}>
      {ann.description}
    </p>

    {/* Attributes table */}
    {ann.attributes.length > 0 && (
      <>
        <h3 style={{ fontSize: '0.85rem', fontWeight: 700, textTransform: 'uppercase', letterSpacing: '0.08em', color: '#475569', marginBottom: '0.75rem' }}>
          Attributes
        </h3>
        <AttrTable attrs={ann.attributes} />
      </>
    )}

    {/* Code example */}
    <h3 style={{ fontSize: '0.85rem', fontWeight: 700, textTransform: 'uppercase', letterSpacing: '0.08em', color: '#475569', marginBottom: '0.5rem' }}>
      Example
    </h3>
    <CodeBlock code={ann.code} language="java" />
  </motion.section>
);

/* ─── Scrollspy sidebar pill ──────────────────────────────────── */
const NavPill = ({ ann, active, onClick }) => (
  <button
    onClick={onClick}
    style={{
      display: 'flex', alignItems: 'center', gap: '0.5rem',
      padding: '0.45rem 0.8rem', borderRadius: '0.5rem', width: '100%',
      textAlign: 'left', cursor: 'pointer', transition: 'all 200ms',
      backgroundColor: active ? 'rgba(234,179,8,0.12)' : 'transparent',
      border: `1px solid ${active ? 'rgba(234,179,8,0.35)' : 'transparent'}`,
      color: active ? '#facc15' : '#64748b',
      fontSize: '0.82rem', fontWeight: active ? 700 : 500,
      fontFamily: 'monospace',
    }}
    onMouseEnter={(e) => { if (!active) { e.currentTarget.style.color = '#cbd5e1'; e.currentTarget.style.backgroundColor = 'rgba(255,255,255,0.04)'; } }}
    onMouseLeave={(e) => { if (!active) { e.currentTarget.style.color = '#64748b'; e.currentTarget.style.backgroundColor = 'transparent'; } }}
  >
    <span style={{ fontSize: '0.9rem' }}>{ann.emoji}</span>
    {ann.name}
  </button>
);

/* ─── Main page ───────────────────────────────────────────────── */
const SDKGuide = () => {
  const [activeId, setActiveId] = useState(ANNOTATIONS[0].id);
  const observerRef = useRef(null);

  useEffect(() => {
    const sectionEls = ANNOTATIONS.map((a) => document.getElementById(a.id)).filter(Boolean);
    if (observerRef.current) observerRef.current.disconnect();

    observerRef.current = new IntersectionObserver(
      (entries) => {
        const visible = entries.filter((e) => e.isIntersecting);
        if (visible.length > 0) {
          const top = visible.sort((a, b) => a.boundingClientRect.top - b.boundingClientRect.top)[0];
          setActiveId(top.target.id);
        }
      },
      { rootMargin: '-20% 0px -60% 0px', threshold: 0 }
    );

    sectionEls.forEach((el) => observerRef.current.observe(el));
    return () => observerRef.current?.disconnect();
  }, []);

  const scrollTo = (id) => {
    const el = document.getElementById(id);
    if (el) el.scrollIntoView({ behavior: 'smooth', block: 'start' });
  };

  return (
    <div style={{ flex: 1, display: 'flex', minHeight: 0 }}>

      {/* ── Left sidebar ─────────────────────────────────────────── */}
      <aside style={{
        width: '220px', flexShrink: 0,
        position: 'sticky', top: '64px', alignSelf: 'flex-start',
        height: 'calc(100vh - 64px)', overflowY: 'auto',
        padding: '2.5rem 1rem 2rem 1.5rem',
        borderRight: '1px solid rgba(255,255,255,0.06)',
      }}>
        <p style={{ fontSize: '0.7rem', fontWeight: 700, textTransform: 'uppercase', letterSpacing: '0.1em', color: '#334155', marginBottom: '0.75rem' }}>
          Annotations
        </p>
        <div style={{ display: 'flex', flexDirection: 'column', gap: '0.2rem' }}>
          {ANNOTATIONS.map((a) => (
            <NavPill key={a.id} ann={a} active={activeId === a.id} onClick={() => scrollTo(a.id)} />
          ))}
        </div>
      </aside>

      {/* ── Main content ────────────────────────────────────────── */}
      <div style={{ flex: 1, overflowY: 'auto', padding: '4rem 3rem 10rem 3.5rem', maxWidth: '860px' }}>

        {/* Page header */}
        <motion.div
          initial={{ opacity: 0, y: 10 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.4 }}
          style={{ marginBottom: '3.5rem' }}
        >
          <div style={{
            display: 'inline-flex', alignItems: 'center', gap: '0.5rem',
            padding: '0.3rem 0.85rem', borderRadius: '999px',
            border: '1px solid rgba(234,179,8,0.25)', backgroundColor: 'rgba(234,179,8,0.08)',
            color: '#facc15', fontSize: '0.78rem', fontWeight: 700,
            marginBottom: '1.25rem', letterSpacing: '0.05em',
          }}>
            ✦ CLIENT SDK
          </div>
          <h1 style={{ fontSize: 'clamp(2rem, 4vw, 3rem)', fontWeight: 800, letterSpacing: '-0.02em', marginBottom: '1rem', lineHeight: 1.1 }}>
            Annotation <span style={{ color: '#facc15' }}>Reference</span>
          </h1>
          <p style={{ fontSize: '1.1rem', color: '#9ca3af', lineHeight: 1.75, maxWidth: '560px' }}>
            Notify.ai integrates into your Spring Boot service via a set of declarative annotations.
            Use this guide to understand what each annotation does and how to wire them together.
          </p>
        </motion.div>

        {/* Installation */}
        <motion.section
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.1 }}
          style={{ marginBottom: '5rem', paddingBottom: '4rem', borderBottom: '1px solid rgba(255,255,255,0.06)' }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', marginBottom: '1.25rem' }}>
            <span style={{ width: '2rem', height: '2rem', borderRadius: '0.5rem', backgroundColor: 'rgba(234,179,8,0.2)', color: '#facc15', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: '0.9rem', fontWeight: 800 }}>1</span>
            <h2 style={{ fontSize: '1.35rem', fontWeight: 700, margin: 0 }}>Installation</h2>
          </div>
          <p style={{ color: '#9ca3af', marginBottom: '0.75rem' }}>Add the client SDK to your Maven project:</p>
          <CodeBlock code={MAVEN_CODE} language="xml" />
        </motion.section>

        {/* All annotation sections */}
        {ANNOTATIONS.map((ann, i) => (
          <AnnotationSection key={ann.id} ann={ann} isFirst={i === 0} />
        ))}

        {/* Quick Start Checklist */}
        <div style={{
          padding: '1.75rem 2rem', borderRadius: '1rem',
          backgroundColor: 'rgba(234,179,8,0.07)', border: '1px solid rgba(234,179,8,0.2)',
          marginTop: '2rem',
        }}>
          <h3 style={{ fontSize: '1.1rem', fontWeight: 700, color: '#facc15', marginBottom: '0.75rem' }}>
            💡 Quick Start Checklist
          </h3>
          <ol style={{ color: '#d1d5db', lineHeight: 2, paddingLeft: '1.25rem', margin: 0 }}>
            <li>Add the Maven dependency.</li>
            <li>Create a <code>@Configuration</code> class and add <code>@EnableNotify(basePackage = "...")</code>.</li>
            <li>Mark your event-emitting service methods with <code>@Event</code>.</li>
            <li>Add <code>@Model</code> + <code>@Vocabulary</code> to your payload classes.</li>
            <li>Write a <code>@SubjectSupplier</code> method to resolve recipients.</li>
            <li>Optionally enrich context with <code>@VocabularySupplier</code>.</li>
            <li>Gate dispatch with <code>@Rule</code> methods and add lifecycle hooks via <code>@Callback</code>.</li>
          </ol>
        </div>
      </div>
    </div>
  );
};

export default SDKGuide;
