-- PGVector 初始化脚本（LangChain4j 官方 PgVectorEmbeddingStore）
-- 在 PostgreSQL 数据库中执行

CREATE EXTENSION IF NOT EXISTS vector;

-- 注意：表结构由应用在启动时通过
-- PgVectorEmbeddingStore.builder().createTable(true)
-- 自动创建。
-- 如需手动建表，请确保与当前 LangChain4j 版本生成的结构完全一致。
