# 🤖 AI Agent Station — 智能 AI Agent平台

[![Java](https://img.shields.io/badge/Java-17-blue.svg)](https://www.oracle.com/java/technologies/javase/jdk17-archive-downloads.html)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.3-green.svg)](https://spring.io/projects/spring-boot)
[![Spring AI](https://img.shields.io/badge/Spring%20AI-1.0.0-orange.svg)](https://spring.io/projects/spring-ai)
[![License](https://img.shields.io/badge/License-Apache%202.0-red.svg)](https://www.apache.org/licenses/LICENSE-2.0)

本项目是一个基于 **Spring Boot 3.4.3** 与 **Spring AI 1.0.0** 的 AI Agent 开发实战项目。系统采用 **DDD（领域驱动设计）** 架构模式，旨在通过工程化手段解决 AI 代理在企业级应用中的配置繁琐、难以扩展等痛点，提供了一套可动态编排、自动装配的 AI 中台解决方案。

---

## 🏗️ 核心设计理念

### 1️⃣ 领域驱动设计 (DDD)
项目严格划分分层职责，确保 **业务逻辑 (Domain)** 与 **技术实现 (Infrastructure)** 深度解耦。

| 层次 | 模块名称 | 定位与核心内容 |
| :--- | :--- | :--- |
| **01. API 层** | `ai-agent-station-study-api` | **接口协议层**：定义对外暴露的契约。包含 `IAiAgentService` 接口、`AutoAgentRequestDTO` 以及统一响应封装 `Response<T>`。 |
| **02. Trigger 层** | `ai-agent-station-study-trigger` | **触发适配层**：系统流量入口。适配标准 RESTful 请求（`AiAgentController`）与异步任务监听。 |
| **03. Domain 层** | `ai-agent-station-study-domain` | **核心领域层**：系统的“大脑”。封装 Armory 军械库装配逻辑、多种执行策略（Flow/Auto）及领域模型（Entity/VO）。 |
| **04. Infrastructure 层** | `ai-agent-station-study-infrastructure` | **基础设施层**：资源实现层。负责 MyBatis DAO（MySQL）、Repository 适配实现 及向量数据库集成。 |
| **05. App 层** | `ai-agent-station-study-app` | **启动配置层**：环境组装中心。管理数据源、线程池、自动装配逻辑（`AiAgentAutoConfiguration`）。 |
| **06. Types 层** | `ai-agent-station-study-types` | **通用类型层**：底层基石。定义全局常量 `Constants`、通用异常 `AppException` 及响应枚举 `ResponseCode`。 |

### 2️⃣ 模块化 AI 编排 (Design Framework)
项目引入了 `xfg-wrench-starter-design-framework` 设计框架（@小傅哥），通过成熟的设计模式编排 AI Agent 的核心能力。

* **🧩 “积木式”构建**：开发者可以像“搭积木”一样，通过组合不同的领域节点（如分析节点、执行节点、总结节点）快速构建具有差异化能力的 AI 助手。
* **📐 设计模式驱动**：
    * **策略模式**：通过 `IExecuteStrategy` 实现不同任务模式（流式编排 vs 自动代理）的灵活切换。
    * **工厂模式**：利用 `DefaultArmoryStrategyFactory` 和 `DefaultAutoAgentExecuteStrategyFactory` 根据环境与配置动态产出执行对象。

### 3️⃣ 数据库驱动的自动装配 (Centralized Configuration)
实现 AI 资源配置的 **集中化管理** 与 **动态初始化**，赋予系统“热更新”能力。

* **📌 配置中心化**：将模型属性、API Key、Prompt 模板、MCP Server 地址等元数据统一存储于数据库中。
* **🔄 动态加载**：利用领域层 `Armory` 模块通过 `ILoadDataStrategy` 策略接口，在请求时实时从数据库提取最新配置。
* **⚡ 自动装配**：系统根据库表记录实例化需要的Client角色（`AdvisorClient`等），实现Agent的构建。

---

## 🚀 核心功能与方案实现

### 🛠️ 动态资源装配方案
* **实现描述**：构建以 `Armory` 为核心的资源装配链条，通过 `AiClientNode`、`AiClientModelNode` 等节点实现对 AI 客户端及其关联配置的组装。
* **方案价值**：彻底摆脱 YAML 静态配置的束缚，支持在线调整 Agent 的模型版本、系统提示词或挂载的工具集。

### 🔌 多模态 AI 能力集成 (Spring AI)
* **标准对接**：深度集成 `spring-ai-starter-model-openai`，提供标准化的模型访问层。
* **MCP 协议扩展**：基于 **Model Context Protocol (MCP)** 协议，支持 Stdio 和 SSE 传输配置，使 Agent 能够动态发现并调用外部搜索或数据工具。

### 📚 RAG 检索增强生成
* **向量化存储**：利用 `PgVector` 与存储库模式实现私有知识库的语义存储与相似度检索。
* **自动化解析**：集成 `Apache Tika` 实现对多格式文档（PDF/TXT 等）的一站式解析与向量入库流程。

### 🔐 企业级工程保障
* **并发性能优化**：通过 `ThreadPoolConfig` 自定义线程池参数，结合 `AsyncConfiguration` 提升 Agent 长链路任务的响应速度。
* **自动化运维**：提供完整的 `Docker Compose` 环境配置文件（MySQL、Nginx、PgVector），支持应用快速容器化部署。

---

## 🛠️ 技术栈清单

| 分类 | 技术选型 |
| :--- | :--- |
| **核心框架** | Java 17, Spring Boot 3.4.3 |
| **AI 增强** | Spring AI v1.0.0 (MCP Client, Vector Store) |
| **持久化与存储** | MyBatis 3.0.4, MySQL 8.0, PgVector |
| **工程化工具** | Lombok, Guava, Docker |

---

## 📂 模块结构说明

```text
ai-agent-station-study
├── ai-agent-station-study-api          // 契约层：定义 DTO（如 AutoAgentRequestDTO）与 Service 接口
├── ai-agent-station-study-app          // 启动层：SpringBoot 配置、线程池 及异步装配逻辑
├── ai-agent-station-study-domain       // 领域层：Agent 执行策略、Armory 资源加载核心
├── ai-agent-station-study-trigger      // 触发层：HTTP 接口（AiAgentController）适配实现
├── ai-agent-station-study-infrastructure // 基础层：DAO 映射、PO 对象 及持久化实现
└── ai-agent-station-study-types        // 类型层：全局通用异常、响应枚举 及常量定义
