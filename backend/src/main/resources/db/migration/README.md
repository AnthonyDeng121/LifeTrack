# Flyway 接管边界

本次全部结构调整保留在 `backend/sql/init_db.sql`，以此最终结构作为版本 1 基线。
这里刻意没有 V1 迁移，也不把本次改动拆成增量迁移。

执行总脚本并检查结构后，仅首次启动使用 `FLYWAY_ADOPT_BASELINE=true`；启动成功后删除该环境变量。
后续数据库变更从 `V2__描述.sql` 开始。已执行的版本脚本禁止修改；新增下一版本。
启动自动校验并执行迁移；Hibernate 只 validate，不再自动改表。禁止 clean。
