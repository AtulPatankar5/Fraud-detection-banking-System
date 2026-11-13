import sys
import pptx
from pptx.util import Inches, Pt
from pptx.dml.color import RGBColor
from pptx.enum.text import PP_ALIGN, MSO_ANCHOR
from pptx.enum.shapes import MSO_SHAPE

def create_deck():
    prs = pptx.Presentation()
    prs.slide_width = Inches(13.333)
    prs.slide_height = Inches(7.5)
    blank_layout = prs.slide_layouts[6]

    # Theme Colors
    BG_DARK = RGBColor(15, 23, 42)       # Slate 900
    CARD_BG = RGBColor(30, 41, 59)       # Slate 800
    CARD_BORDER = RGBColor(51, 65, 85)   # Slate 700
    TEXT_WHITE = RGBColor(248, 250, 252) # Slate 50
    TEXT_MUTED = RGBColor(148, 163, 184) # Slate 400
    TEXT_DIM = RGBColor(100, 116, 139)   # Slate 500

    # Accents
    BLUE = RGBColor(37, 99, 235)         # Blue 600
    CYAN = RGBColor(6, 182, 212)         # Cyan 500
    EMERALD = RGBColor(16, 185, 129)     # Emerald 500
    AMBER = RGBColor(245, 158, 11)       # Amber 500
    ROSE = RGBColor(244, 63, 94)         # Rose 500
    PURPLE = RGBColor(139, 92, 246)      # Violet 500
    ORANGE = RGBColor(249, 115, 22)      # Orange 500

    def add_bg(slide):
        bg = slide.shapes.add_shape(MSO_SHAPE.RECTANGLE, 0, 0, Inches(13.333), Inches(7.5))
        bg.fill.solid()
        bg.fill.fore_color.rgb = BG_DARK
        bg.line.fill.background()

    def add_header(slide, title, category="DISTRIBUTED BANKING SYSTEM • SYSTEM DESIGN & INTERVIEW NOTES"):
        # Category badge
        cat_box = slide.shapes.add_textbox(Inches(0.8), Inches(0.4), Inches(11.7), Inches(0.4))
        tf_c = cat_box.text_frame
        tf_c.word_wrap = True
        p_c = tf_c.paragraphs[0]
        p_c.text = category.upper()
        p_c.font.size = Pt(10)
        p_c.font.bold = True
        p_c.font.color.rgb = CYAN

        # Title
        t_box = slide.shapes.add_textbox(Inches(0.8), Inches(0.7), Inches(11.7), Inches(0.7))
        tf_t = t_box.text_frame
        tf_t.word_wrap = True
        p_t = tf_t.paragraphs[0]
        p_t.text = title
        p_t.font.size = Pt(22)
        p_t.font.bold = True
        p_t.font.color.rgb = TEXT_WHITE

    # -------------------------------------------------------------
    # SLIDE 1: TITLE SLIDE
    # -------------------------------------------------------------
    s1 = prs.slides.add_slide(blank_layout)
    add_bg(s1)

    # Accent decorative banner
    banner = s1.shapes.add_shape(MSO_SHAPE.RECTANGLE, Inches(0.8), Inches(1.2), Inches(0.1), Inches(4.8))
    banner.fill.solid()
    banner.fill.fore_color.rgb = BLUE
    banner.line.fill.background()

    tb = s1.shapes.add_textbox(Inches(1.2), Inches(1.3), Inches(11), Inches(4.5))
    tf = tb.text_frame
    tf.word_wrap = True

    p0 = tf.paragraphs[0]
    p0.text = "EVENT-DRIVEN ENTERPRISE SYSTEM DESIGN"
    p0.font.size = Pt(12)
    p0.font.bold = True
    p0.font.color.rgb = CYAN
    p0.space_after = Pt(12)

    p1 = tf.add_paragraph()
    p1.text = "Distributed Banking & Real-Time\nFraud Detection System"
    p1.font.size = Pt(36)
    p1.font.bold = True
    p1.font.color.rgb = TEXT_WHITE
    p1.space_after = Pt(18)

    p2 = tf.add_paragraph()
    p2.text = "End-to-End Architectural Deep Dive, Kafka Event Choreography, Redis In-Memory State & SAGA Consistency"
    p2.font.size = Pt(14)
    p2.font.color.rgb = TEXT_MUTED
    p2.space_after = Pt(24)

    # Tech badges
    p3 = tf.add_paragraph()
    p3.text = "TECH STACK: Java 17  |  Spring Boot 3  |  Spring Cloud Gateway  |  Apache Kafka  |  Redis  |  MySQL 8  |  Razorpay SDK"
    p3.font.size = Pt(11)
    p3.font.bold = True
    p3.font.color.rgb = EMERALD

    # 3 Summary Cards at bottom
    highlights = [
        ("SAGA Pattern", "Eventual data consistency across isolated DBs with automatic compensating refunds.", PURPLE),
        ("Sub-ms Fraud Engine", "Multi-tiered checks using Redis: Velocity counters, moving averages & balance limits.", AMBER),
        ("Event-Driven Kafka", "Decoupled asynchronous streaming across 9 dedicated topics with idempotency.", BLUE)
    ]
    for i, (title, desc, color) in enumerate(highlights):
        cx = Inches(0.8 + i * 4.0)
        cy = Inches(5.4)
        cbox = s1.shapes.add_shape(MSO_SHAPE.ROUNDED_RECTANGLE, cx, cy, Inches(3.7), Inches(1.5))
        cbox.fill.solid()
        cbox.fill.fore_color.rgb = CARD_BG
        cbox.line.color.rgb = color
        cbox.line.width = Pt(1.5)

        ctb = s1.shapes.add_textbox(cx + Inches(0.15), cy + Inches(0.1), Inches(3.4), Inches(1.3))
        ctf = ctb.text_frame
        ctf.word_wrap = True
        cp0 = ctf.paragraphs[0]
        cp0.text = title
        cp0.font.size = Pt(13)
        cp0.font.bold = True
        cp0.font.color.rgb = color
        cp0.space_after = Pt(4)

        cp1 = ctf.add_paragraph()
        cp1.text = desc
        cp1.font.size = Pt(10)
        cp1.font.color.rgb = TEXT_MUTED

    # -------------------------------------------------------------
    # SLIDE 2: HIGH-LEVEL ARCHITECTURE MAP
    # -------------------------------------------------------------
    s2 = prs.slides.add_slide(blank_layout)
    add_bg(s2)
    add_header(s2, "System Architecture & Microservices Landscape")

    services = [
        ("API Gateway", "Port: 8080", "• Spring Cloud Gateway\n• Redis Token Bucket Limiter\n• Client IP Host Resolver\n• Reverse Proxy Routes", PURPLE, 0.8, 1.6),
        ("Transaction Service", "Port: 8082 | MySQL", "• SAGA Coordinator\n• Transfer Lifecycle Engine\n• Feign Client to Account\n• OTP Generation & Verify", BLUE, 4.0, 1.6),
        ("Account Service", "Port: 8081 | MySQL", "• Account & Balance CRUD\n• Deduct & Credit APIs\n• Consumer: Txn Completed\n• Consumer: Fraud Detected (Block)", CYAN, 7.2, 1.6),
        ("Fraud Detection", "Port: 8084 | Redis", "• 3-Rule Fraud Analytics\n• Velocity Counter (60s)\n• Moving Average Spikes\n• Balance Ratio (>90%)", AMBER, 10.4, 1.6),
        ("Notification Service", "Port: 8085 | Stateless", "• Omnichannel Alert Hub\n• Debit & Credit Receipts\n• OTP Verification Alerts\n• Refund & Fraud Warnings", EMERALD, 4.0, 4.5),
        ("Payment Service", "Port: 8083 | MySQL", "• Razorpay SDK Integration\n• Order Creation (/create-order)\n• Webhook Callback Engine\n• Kafka Payment Event Bus", ROSE, 7.2, 4.5),
        ("Infrastructure Backbone", "Kafka (9092) & Redis (6379)", "• Kafka: 9 High-throughput topics\n• Redis: Rate limiting, OTP & fraud window\n• MySQL: Database-per-service pattern", ORANGE, 10.4, 4.5),
        ("Client Entry Point", "Web / Mobile Clients", "• REST API Calls (HTTP)\n• JSON Payloads\n• Rate-Limited via Gateway", TEXT_MUTED, 0.8, 4.5)
    ]

    for title, subtitle, details, color, x, y in services:
        box = s2.shapes.add_shape(MSO_SHAPE.ROUNDED_RECTANGLE, Inches(x), Inches(y), Inches(2.9), Inches(2.5))
        box.fill.solid()
        box.fill.fore_color.rgb = CARD_BG
        box.line.color.rgb = color
        box.line.width = Pt(1.5)

        tb = s2.shapes.add_textbox(Inches(x + 0.1), Inches(y + 0.1), Inches(2.7), Inches(2.3))
        tf = tb.text_frame
        tf.word_wrap = True

        p_t = tf.paragraphs[0]
        p_t.text = title
        p_t.font.size = Pt(13)
        p_t.font.bold = True
        p_t.font.color.rgb = color

        p_s = tf.add_paragraph()
        p_s.text = subtitle
        p_s.font.size = Pt(9.5)
        p_s.font.bold = True
        p_s.font.color.rgb = TEXT_MUTED
        p_s.space_after = Pt(6)

        p_d = tf.add_paragraph()
        p_d.text = details
        p_d.font.size = Pt(9)
        p_d.font.color.rgb = TEXT_WHITE

    # -------------------------------------------------------------
    # SLIDE 3: END-TO-END MONEY TRANSFER FLOW (HAPPY PATH)
    # -------------------------------------------------------------
    s3 = prs.slides.add_slide(blank_layout)
    add_bg(s3)
    add_header(s3, "End-to-End Money Transfer Flow (Clean Path)", "CORE TRANSACTION WORKFLOW • SAGA FORWARD STEPS")

    flow_steps = [
        ("Step 1", "Client Request", "User submits POST /transfer\nGateway validates rate limit via Redis\nRoutes to Transaction Service", PURPLE),
        ("Step 2", "SAGA Step 1 (Deduct)", "Transaction Svc calls Account Svc\nvia OpenFeign (Synchronous)\nSender balance deducted immediately", BLUE),
        ("Step 3", "Kafka Event Dispatched", "Transaction saved as PROCESSING\nPublishes 'transaction.initiated'\nto Kafka topic with txn details", ORANGE),
        ("Step 4", "Fraud Evaluation", "Fraud Service consumes event\nRuns 3 in-memory checks in Redis\nEmits 'fraud.check.clean'", AMBER),
        ("Step 5", "Final Settlement", "Transaction Service marks COMPLETED\nPublishes 'transaction.completed'\nReceiver credited & alerts sent", EMERALD)
    ]

    for i, (num, step_title, step_desc, color) in enumerate(flow_steps):
        x = 0.8 + i * 2.4
        box = s3.shapes.add_shape(MSO_SHAPE.ROUNDED_RECTANGLE, Inches(x), Inches(1.8), Inches(2.2), Inches(3.6))
        box.fill.solid()
        box.fill.fore_color.rgb = CARD_BG
        box.line.color.rgb = color
        box.line.width = Pt(1.5)

        tb = s3.shapes.add_textbox(Inches(x + 0.1), Inches(1.9), Inches(2.0), Inches(3.4))
        tf = tb.text_frame
        tf.word_wrap = True

        p_num = tf.paragraphs[0]
        p_num.text = num.upper()
        p_num.font.size = Pt(10)
        p_num.font.bold = True
        p_num.font.color.rgb = color

        p_st = tf.add_paragraph()
        p_st.text = step_title
        p_st.font.size = Pt(13)
        p_st.font.bold = True
        p_st.font.color.rgb = TEXT_WHITE
        p_st.space_after = Pt(10)

        p_sd = tf.add_paragraph()
        p_sd.text = step_desc
        p_sd.font.size = Pt(10)
        p_sd.font.color.rgb = TEXT_MUTED

    # Technical takeaway box at bottom
    tbox = s3.shapes.add_shape(MSO_SHAPE.RECTANGLE, Inches(0.8), Inches(5.7), Inches(11.7), Inches(1.2))
    tbox.fill.solid()
    tbox.fill.fore_color.rgb = CARD_BG
    tbox.line.color.rgb = CARD_BORDER

    tb2 = s3.shapes.add_textbox(Inches(1.0), Inches(5.8), Inches(11.3), Inches(1.0))
    tf2 = tb2.text_frame
    tf2.word_wrap = True
    tp0 = tf2.paragraphs[0]
    tp0.text = "KEY ARCHITECTURAL DECISION: SYNCHRONOUS DEDUCTION + ASYNCHRONOUS SETTLEMENT"
    tp0.font.size = Pt(11)
    tp0.font.bold = True
    tp0.font.color.rgb = CYAN

    tp1 = tf2.add_paragraph()
    tp1.text = "Deducting from the sender synchronously via OpenFeign prevents double-spending and ensures invariant checks (sufficient balance, active status) pass immediately. Final receiver crediting and notifications are decoupled asynchronously through Kafka."
    tp1.font.size = Pt(10)
    tp1.font.color.rgb = TEXT_WHITE

    # -------------------------------------------------------------
    # SLIDE 4: FRAUD DETECTION ENGINE DEEP DIVE
    # -------------------------------------------------------------
    s4 = prs.slides.add_slide(blank_layout)
    add_bg(s4)
    add_header(s4, "Real-Time Fraud Detection Engine (Sub-Millisecond)", "ALGORITHMIC RULES • REDIS IN-MEMORY ENGINE")

    rules = [
        ("RULE 1: VELOCITY COUNTER", "Key: fraud:velocity:{accountNumber}", "Sliding 60-Second Window",
         "• Uses Redis atomic INCR on transaction event\n• On count == 1, sets key EXPIRE 60 SECONDS\n• Threshold: > 5 transactions/min flags account\n• Catches automated bot attacks and rapid draining", AMBER, 0.8),
        ("RULE 2: SPENDING SPIKE (AVERAGE)", "Key: fraud.avg_amount:{accountNumber}", "Exponential Moving Average",
         "• Stores baseline spending amount in Redis RAM\n• Multiplier Threshold: 5X historical average\n• Recalculates new average: (avg + amount) / 2\n• Flags abnormal high-value sudden transfers", ORANGE, 4.8),
        ("RULE 3: BALANCE RATIO DRAIN", "Feign: AccountServiceClient.getBalance()", "Liquid Balance Protection",
         "• Synchronously queries current balance\n• Threshold: Transfer amount > 90% of total balance\n• Prevents complete sudden account drainage\n• Requires explicit 2FA challenge before clearing", ROSE, 8.8)
    ]

    for title, key_info, badge, bullets, color, x in rules:
        box = s4.shapes.add_shape(MSO_SHAPE.ROUNDED_RECTANGLE, Inches(x), Inches(1.7), Inches(3.7), Inches(4.0))
        box.fill.solid()
        box.fill.fore_color.rgb = CARD_BG
        box.line.color.rgb = color
        box.line.width = Pt(1.5)

        tb = s4.shapes.add_textbox(Inches(x + 0.15), Inches(1.8), Inches(3.4), Inches(3.8))
        tf = tb.text_frame
        tf.word_wrap = True

        p0 = tf.paragraphs[0]
        p0.text = title
        p0.font.size = Pt(12)
        p0.font.bold = True
        p0.font.color.rgb = color

        p1 = tf.add_paragraph()
        p1.text = key_info
        p1.font.size = Pt(9.5)
        p1.font.bold = True
        p1.font.color.rgb = CYAN

        p2 = tf.add_paragraph()
        p2.text = f"Pattern: {badge}"
        p2.font.size = Pt(9.5)
        p2.font.color.rgb = TEXT_MUTED
        p2.space_after = Pt(10)

        p3 = tf.add_paragraph()
        p3.text = bullets
        p3.font.size = Pt(10)
        p3.font.color.rgb = TEXT_WHITE

    # Outcomes Box
    out_box = s4.shapes.add_shape(MSO_SHAPE.RECTANGLE, Inches(0.8), Inches(5.9), Inches(11.7), Inches(1.1))
    out_box.fill.solid()
    out_box.fill.fore_color.rgb = CARD_BG
    out_box.line.color.rgb = CARD_BORDER

    otb = s4.shapes.add_textbox(Inches(1.0), Inches(5.95), Inches(11.3), Inches(0.9))
    otf = otb.text_frame
    otf.word_wrap = True
    op0 = otf.paragraphs[0]
    op0.text = "ENGINE OUTCOMES VIA KAFKA:"
    op0.font.size = Pt(10)
    op0.font.bold = True
    op0.font.color.rgb = CYAN

    op1 = otf.add_paragraph()
    op1.text = "1. ALL CHECKS PASS: Emits 'fraud.check.clean' ➔ Transaction completes normally.\n2. ANY CHECK FAILS: Emits 'verification.required' ➔ Halts flow, generates OTP with 5m TTL, challenges user."
    op1.font.size = Pt(10)
    op1.font.color.rgb = TEXT_WHITE

    # -------------------------------------------------------------
    # SLIDE 5: OTP VERIFICATION & SAGA COMPENSATION (ROLLBACK)
    # -------------------------------------------------------------
    s5 = prs.slides.add_slide(blank_layout)
    add_bg(s5)
    add_header(s5, "OTP Verification & SAGA Compensating Actions", "DISTRIBUTED RESILIENCY • FAILURE RECOVERY FLOW")

    paths = [
        ("SCENARIO A: OTP VERIFIED", "Happy Path Verification",
         "1. User calls POST /verify?otp=...\n2. OTP matched in Redis (verification:otp{txId})\n3. Key deleted immediately (anti-replay)\n4. Status updated to COMPLETED\n5. Kafka 'transaction.completed' emitted\n6. Receiver account credited by Account Service",
         EMERALD, 0.8),
        ("SCENARIO B: WRONG OTP (ATTACK)", "Security Incident & Account Freeze",
         "1. User submits mismatched OTP\n2. Key deleted immediately from Redis\n3. SAGA Compensation: Sender refunded via Feign\n4. Emits 'fraud.detected' to Kafka\n5. Account Service sets status = BLOCKED\n6. Critical alert sent to legitimate user",
         ROSE, 4.8),
        ("SCENARIO C: OTP EXPIRED (TIMEOUT)", "Automatic Expiry & Refund",
         "1. User submits OTP after 5 minutes\n2. Key expired & evicted by Redis (returns null)\n3. Transaction Service halts settlement\n4. SAGA Compensation: Sender refunded via Feign\n5. Status updated to FLAGGED in MySQL\n6. Emits 'transaction.refunded' to user",
         AMBER, 8.8)
    ]

    for title, subtitle, bullets, color, x in paths:
        box = s5.shapes.add_shape(MSO_SHAPE.ROUNDED_RECTANGLE, Inches(x), Inches(1.7), Inches(3.7), Inches(4.1))
        box.fill.solid()
        box.fill.fore_color.rgb = CARD_BG
        box.line.color.rgb = color
        box.line.width = Pt(1.5)

        tb = s5.shapes.add_textbox(Inches(x + 0.15), Inches(1.8), Inches(3.4), Inches(3.9))
        tf = tb.text_frame
        tf.word_wrap = True

        p0 = tf.paragraphs[0]
        p0.text = title
        p0.font.size = Pt(12)
        p0.font.bold = True
        p0.font.color.rgb = color

        p1 = tf.add_paragraph()
        p1.text = subtitle
        p1.font.size = Pt(9.5)
        p1.font.color.rgb = TEXT_MUTED
        p1.space_after = Pt(10)

        p2 = tf.add_paragraph()
        p2.text = bullets
        p2.font.size = Pt(10)
        p2.font.color.rgb = TEXT_WHITE

    # Key Principle Box
    kp_box = s5.shapes.add_shape(MSO_SHAPE.RECTANGLE, Inches(0.8), Inches(6.0), Inches(11.7), Inches(1.0))
    kp_box.fill.solid()
    kp_box.fill.fore_color.rgb = CARD_BG
    kp_box.line.color.rgb = CARD_BORDER

    kptb = s5.shapes.add_textbox(Inches(1.0), Inches(6.05), Inches(11.3), Inches(0.9))
    kptf = kptb.text_frame
    kptf.word_wrap = True
    kpp0 = kptf.paragraphs[0]
    kpp0.text = "SAGA COMPENSATION PRINCIPLE:"
    kpp0.font.size = Pt(10)
    kpp0.font.bold = True
    kpp0.font.color.rgb = CYAN

    kpp1 = kptf.add_paragraph()
    kpp1.text = "In distributed systems without two-phase commit, failure recovery requires an explicit forward compensating transaction (creditBalance) to restore initial state, guaranteeing Eventual Consistency across decoupled database instances."
    kpp1.font.size = Pt(10)
    kpp1.font.color.rgb = TEXT_WHITE

    # -------------------------------------------------------------
    # SLIDE 6: KAFKA TOPICS & REDIS MATRIX
    # -------------------------------------------------------------
    s6 = prs.slides.add_slide(blank_layout)
    add_bg(s6)
    add_header(s6, "Kafka Event Registry & Redis State Matrix", "INFRASTRUCTURE MAPPING • EVENT-DRIVEN COMMUNICATION")

    # Kafka Table
    k_header = s6.shapes.add_textbox(Inches(0.8), Inches(1.5), Inches(6.0), Inches(0.3))
    k_header.text_frame.paragraphs[0].text = "APACHE KAFKA TOPICS REGISTRY (PORT 9092)"
    k_header.text_frame.paragraphs[0].font.size = Pt(11)
    k_header.text_frame.paragraphs[0].font.bold = True
    k_header.text_frame.paragraphs[0].font.color.rgb = ORANGE

    k_rows = [
        ("transaction.initiated", "Txn Svc", "Fraud Svc", "Trigger 3 fraud checks"),
        ("fraud.check.clean", "Fraud Svc", "Txn Svc", "Authorize txn completion"),
        ("verification.required", "Fraud Svc", "Txn Svc", "Trigger OTP challenge"),
        ("transaction.otp.generated", "Txn Svc", "Notif Svc", "Send OTP SMS/Email"),
        ("transaction.completed", "Txn Svc", "Account, Notif", "Credit receiver & receipts"),
        ("fraud.detected", "Txn Svc", "Account, Notif", "Block compromised account"),
        ("transaction.refunded", "Txn Svc", "Notif Svc", "Alert sender of refund"),
        ("payment.completed / failed", "Payment", "Notif Svc", "Razorpay webhook alert")
    ]

    for i, (top, prod, cons, desc) in enumerate(k_rows):
        y = 1.9 + i * 0.55
        kbox = s6.shapes.add_shape(MSO_SHAPE.ROUNDED_RECTANGLE, Inches(0.8), Inches(y), Inches(5.8), Inches(0.5))
        kbox.fill.solid()
        kbox.fill.fore_color.rgb = CARD_BG
        kbox.line.color.rgb = CARD_BORDER

        ktb = s6.shapes.add_textbox(Inches(0.9), Inches(y + 0.05), Inches(5.6), Inches(0.4))
        ktf = ktb.text_frame
        ktf.word_wrap = True
        kp = ktf.paragraphs[0]
        kp.text = f"{top}  [{prod} ➔ {cons}]"
        kp.font.size = Pt(9.5)
        kp.font.bold = True
        kp.font.color.rgb = CYAN

        kp2 = ktf.add_paragraph()
        kp2.text = desc
        kp2.font.size = Pt(8.5)
        kp2.font.color.rgb = TEXT_MUTED

    # Redis Table
    r_header = s6.shapes.add_textbox(Inches(7.0), Inches(1.5), Inches(5.5), Inches(0.3))
    r_header.text_frame.paragraphs[0].text = "REDIS STATE ENGINE USE CASES (PORT 6379)"
    r_header.text_frame.paragraphs[0].font.size = Pt(11)
    r_header.text_frame.paragraphs[0].font.bold = True
    r_header.text_frame.paragraphs[0].font.color.rgb = ROSE

    r_cards = [
        ("1. API Gateway Rate Limiting", "Token Bucket per Client IP", "• Spring Cloud Gateway RedisRateLimiter\n• Replenish Rate: 10/s | Burst: 20 tokens\n• Prevents DDoS and brute-force API abuse", PURPLE),
        ("2. Fraud Velocity Tracker", "Key: fraud:velocity:{accountNumber}", "• Atomic INCR on transaction initiation\n• TTL: 60 Seconds (Auto-resets sliding window)\n• Limit: > 5 tx/min triggers OTP challenge", AMBER),
        ("3. Spending Anomaly Baseline", "Key: fraud.avg_amount:{accountNumber}", "• Key-Value store holding moving average\n• Formula: (previousAvg + currentAmount) / 2\n• Flags if amount > 5X historical average", ORANGE),
        ("4. OTP Challenge Store", "Key: verification:otp{transactionId}", "• 6-digit numeric OTP generated in memory\n• TTL: 5 Minutes (auto-expires on timeout)\n• Immediate deletion on read (anti-replay)", EMERALD)
    ]

    for i, (rtitle, rkey, rdesc, rcolor) in enumerate(r_cards):
        y = 1.9 + i * 1.2
        rbox = s6.shapes.add_shape(MSO_SHAPE.ROUNDED_RECTANGLE, Inches(7.0), Inches(y), Inches(5.5), Inches(1.1))
        rbox.fill.solid()
        rbox.fill.fore_color.rgb = CARD_BG
        rbox.line.color.rgb = rcolor
        rbox.line.width = Pt(1.5)

        rtb = s6.shapes.add_textbox(Inches(7.1), Inches(y + 0.05), Inches(5.3), Inches(1.0))
        rtf = rtb.text_frame
        rtf.word_wrap = True
        rp0 = rtf.paragraphs[0]
        rp0.text = f"{rtitle}  ({rkey})"
        rp0.font.size = Pt(10)
        rp0.font.bold = True
        rp0.font.color.rgb = rcolor

        rp1 = rtf.add_paragraph()
        rp1.text = rdesc
        rp1.font.size = Pt(8.5)
        rp1.font.color.rgb = TEXT_WHITE

    # -------------------------------------------------------------
    # SLIDE 7: RAZORPAY PAYMENT GATEWAY & WEBHOOK ARCHITECTURE
    # -------------------------------------------------------------
    s7 = prs.slides.add_slide(blank_layout)
    add_bg(s7)
    add_header(s7, "Razorpay Payment Gateway & Webhook Architecture", "EXTERNAL INTEGRATION • EVENT PROPAGATION")

    pay_steps = [
        ("Step 1: Order Creation", "POST /api/v1/payments/create-order\n• Amount converted to paise/cents (*100)\n• RazorpayClient calls orders.create()\n• Saved in MySQL with status CREATED\n• Returns orderId & razorpayKeyId to UI", BLUE, 0.8),
        ("Step 2: Client Checkout", "Frontend Razorpay Checkout Modal\n• User enters Card / UPI credentials\n• Payment processed on Razorpay rails\n• Bank OTP verification executed\n• Razorpay confirms payment capture", PURPLE, 3.8),
        ("Step 3: Webhook Ingestion", "POST /api/v1/payments/webhook\n• Razorpay posts event asynchronously\n• Parses 'payment.captured' payload\n• Finds Payment entity by order_id\n• Sets status COMPLETED & saves paymentId", EMERALD, 6.8),
        ("Step 4: Kafka Notification", "Publishes 'payment.completed' to Kafka\n• Notification Service consumes event\n• Sends confirmation receipt to customer\n• If event is 'payment.failed': marks FAILED\n  and dispatches failure alert", ORANGE, 9.8)
    ]

    for title, desc, color, x in pay_steps:
        box = s7.shapes.add_shape(MSO_SHAPE.ROUNDED_RECTANGLE, Inches(x), Inches(1.8), Inches(2.7), Inches(4.2))
        box.fill.solid()
        box.fill.fore_color.rgb = CARD_BG
        box.line.color.rgb = color
        box.line.width = Pt(1.5)

        tb = s7.shapes.add_textbox(Inches(x + 0.15), Inches(1.9), Inches(2.4), Inches(4.0))
        tf = tb.text_frame
        tf.word_wrap = True

        p0 = tf.paragraphs[0]
        p0.text = title
        p0.font.size = Pt(12)
        p0.font.bold = True
        p0.font.color.rgb = color
        p0.space_after = Pt(10)

        p1 = tf.add_paragraph()
        p1.text = desc
        p1.font.size = Pt(10)
        p1.font.color.rgb = TEXT_WHITE

    # Webhook Idempotency box
    wbox = s7.shapes.add_shape(MSO_SHAPE.RECTANGLE, Inches(0.8), Inches(6.1), Inches(11.7), Inches(0.9))
    wbox.fill.solid()
    wbox.fill.fore_color.rgb = CARD_BG
    wbox.line.color.rgb = CARD_BORDER

    wtb = s7.shapes.add_textbox(Inches(1.0), Inches(6.15), Inches(11.3), Inches(0.8))
    wtf = wtb.text_frame
    wtf.word_wrap = True
    wp0 = wtf.paragraphs[0]
    wp0.text = "WEBHOOK IDEMPOTENCY:"
    wp0.font.size = Pt(10)
    wp0.font.bold = True
    wp0.font.color.rgb = CYAN

    wp1 = wtf.add_paragraph()
    wp1.text = "Payment gateway webhooks can be retried multiple times by Razorpay if network acknowledgements are delayed. The service matches by razorpayOrderId to guarantee idempotent state transitions."
    wp1.font.size = Pt(10)
    wp1.font.color.rgb = TEXT_WHITE

    # -------------------------------------------------------------
    # SLIDE 8: INTERVIEW CHEAT SHEET & ARCHITECTURE HIGHLIGHTS
    # -------------------------------------------------------------
    s8 = prs.slides.add_slide(blank_layout)
    add_bg(s8)
    add_header(s8, "Interview Talking Points & System Trade-Offs", "SYSTEM DESIGN SUMMARY • HIGH-IMPACT ANSWERS")

    qa_cards = [
        ("Why SAGA over 2-Phase Commit (2PC)?",
         "2PC uses blocking distributed locks across all databases, creating single points of failure and low throughput. SAGA uses local transactions with compensating rollbacks (creditBalance), prioritizing high availability (AP in CAP Theorem).",
         BLUE, 0.8, 1.7),
        ("Why OpenFeign + Kafka Hybrid?",
         "OpenFeign synchronously validates strict business invariants (balance sufficiency, active status) before accepting the transfer. Kafka asynchronously handles decoupled, non-blocking steps (fraud check, receiver credit, notifications).",
         CYAN, 6.8, 1.7),
        ("How to Guarantee Kafka Idempotency?",
         "Consumers verify state preconditions before execution: TransactionService checks 'status == PROCESSING' before completing or challenging; duplicate events are discarded safely without double-crediting.",
         EMERALD, 0.8, 4.3),
        ("How to Scale to 10,000 TPS?",
         "1. Partition Kafka topics by accountNumber for strict in-order processing per account.\n2. Scale Redis Cluster with multi-node sharding.\n3. Implement MySQL read-replicas for query endpoints.\n4. Adopt Transactional Outbox Pattern with Debezium CDC.",
         AMBER, 6.8, 4.3)
    ]

    for q, a, color, x, y in qa_cards:
        box = s8.shapes.add_shape(MSO_SHAPE.ROUNDED_RECTANGLE, Inches(x), Inches(y), Inches(5.7), Inches(2.4))
        box.fill.solid()
        box.fill.fore_color.rgb = CARD_BG
        box.line.color.rgb = color
        box.line.width = Pt(1.5)

        tb = s8.shapes.add_textbox(Inches(x + 0.15), Inches(y + 0.1), Inches(5.4), Inches(2.2))
        tf = tb.text_frame
        tf.word_wrap = True

        pq = tf.paragraphs[0]
        pq.text = q
        pq.font.size = Pt(12)
        pq.font.bold = True
        pq.font.color.rgb = color
        pq.space_after = Pt(8)

        pa = tf.add_paragraph()
        pa.text = a
        pa.font.size = Pt(10)
        pa.font.color.rgb = TEXT_WHITE

    # Save presentation
    output_path = r"D:\Java Projects\Banking-system\interview-notes\Banking_System_Architecture_and_Flows.pptx"
    prs.save(output_path)
    print(f"Presentation saved successfully to {output_path}")

if __name__ == "__main__":
    create_deck()

