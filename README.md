# LumeBridge: High-Performance AI Gateway

LumeBridge is a production-grade AI Gateway engineered for high-concurrency, security-first AI workloads. Built on **Java 21 with Project Loom (Virtual Threads)**, it provides an ultra-low-latency bridge between your applications and LLM providers.

---

## 🏗️ Architecture Overiew

- **Core**: Java 21 / Maven (Optimized for Virtual Thread throughput)
- **Persistence**: PostgreSQL (Metadata & Task Audit)
- **Caching**: Redis (Semantic Cache & Nonce Validation)
- **Stream**: Redpanda (Security DLQ & Real-time Metrics)
- **Infrastructure**: Docker-native with a fixed project identity (`lumebridge`)

## 📂 Project Structure

```bash
.
├── core/java        # High-concurrency gateway core
├── infra/           # Docker Compose & Infrastructure scripts
├── config/          # Configuration templates
├── scripts/         # Benchmark & utility runners
├── benchmarks/      # Performance test suites (k6)
└── reports/         # Automated stress test results & analysis
```

## 🚀 Quick Start

### 1. Initialize Infrastructure
```bash
make up
```

### 2. Configure
Copy the example configuration and add your provider keys:
```bash
cp config/lumebridge.example.yaml lumebridge.yaml
```

### 3. Build & Run
```bash
make build
make run PROFILE=ai-pro
```

## 📊 Performance & Security Profiles

- **`api-pro`**: High-speed mode optimized for JSON/API throughput. Includes nonce validation and collision prevention.
- **`ai-pro`**: Security-hardened mode with PII scrubbing, safety guardrails, and intent classification.

## 🛡️ Benchmarks & Hardening
The gateway has been stress-tested with **10,000 requests** under high concurrency (50 VUs). Detailed results, including security audit logs (Auth, Safety, Collision, Nonce), can be found in:
👉 [Gateway Performance Report](reports/gateway_performance_report.md)

## ☸️ Production Deployment (K8s)
For production environments, LumeBridge is designed to run on **Kubernetes**.
- **Scaling**: Horizontal Pod Autoscaling (HPA) based on Virtual Thread latency.
- **Config**: Mount `lumebridge.yaml` via ConfigMaps.
- **High Availability**: Multi-zone deployment recommended for the Redis/Postgres persistence layer.

---
© 2026 LumeBridge Architecture Team
