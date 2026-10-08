# 客服 Agent 部署说明

## 1. 初始化数据库

在已有 `hmdp` 数据库执行 `src/main/resources/db/ai_customer_service.sql`。脚本只创建 `tb_ai_conversation` 和 `tb_ai_chat_message`，不会修改已有业务表。

## 2. 配置模型

客服接口使用 OpenAI Chat Completions 兼容协议。启动后端前设置：

```text
AI_ENABLED=true
AI_BASE_URL=https://your-provider.example/v1
AI_API_KEY=your-secret-key
AI_MODEL=your-model-name
AI_TIMEOUT_MS=30000
```

`AI_ENABLED=false`（默认）时接口仍可启动，但发送消息会返回未配置提示，不会产生模型请求。

## 3. API

所有接口都要求现有登录 Token：

```text
POST /api/ai/customer-service/conversations
GET  /api/ai/customer-service/conversations/{id}/messages
POST /api/ai/customer-service/conversations/{id}/messages
```

消息请求体：

```json
{"content":"这家店有哪些优惠券？","requestId":"web-unique-id"}
```

客服只开放店铺查询、店铺详情、店铺优惠券和当前用户订单四个只读工具。Redis 保存最近 16 条消息并设置 30 天 TTL；Redis 缓存缺失时由 MySQL 消息自动回填。

## 4. 前端

Nginx 仍监听 `8080`，前端通过 `/api` 反向代理到后端。登录后访问 `/ai-chat.html`，或从店铺详情和底部导航进入。前端文件位于 `E:/nginx1/nginx-1.18.0/html/hmdp`，与后端保持分离部署。
