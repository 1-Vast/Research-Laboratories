-- =====================================================================
-- LabFlow 种子数据（第一阶段）
-- 全部为虚构演示数据；密码使用 BCrypt 加盐哈希，统一演示口令见 README。
-- 脚本通过子查询按 code 关联，不依赖硬编码自增 ID，可在全新库上一次执行。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 角色
-- ---------------------------------------------------------------------
INSERT INTO roles (code, name, description, protected, created_at, updated_at) VALUES
    ('SYS_ADMIN', '系统管理员', '管理用户、角色、权限与实验室登记，默认不读取科研正文', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('PI', '实验室负责人', '管理本实验室成员，审批立项、结项与资源预约', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('PROJECT_LEADER', '科研项目负责人', '管理所负责项目的成员、任务、周报与成果审核', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('RESEARCHER', '科研人员', '承接任务、提交周报、申请资源、共享成果与知识', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- ---------------------------------------------------------------------
-- 权限目录（接口操作码）
-- ---------------------------------------------------------------------
INSERT INTO permissions (code, description, created_at) VALUES
    ('user:manage', '用户管理：增删改查、启用禁用、分配角色', CURRENT_TIMESTAMP),
    ('role:manage', '角色与权限管理：增删改查、分配权限', CURRENT_TIMESTAMP),
    ('lab:create', '创建实验室', CURRENT_TIMESTAMP),
    ('lab:appoint', '任命实验室负责人（PI）', CURRENT_TIMESTAMP),
    ('lab:read', '查看所属实验室信息', CURRENT_TIMESTAMP),
    ('lab:member:manage', '管理本实验室成员', CURRENT_TIMESTAMP),
    ('project:create', '创建项目草稿', CURRENT_TIMESTAMP),
    ('project:read', '查看项目与进度', CURRENT_TIMESTAMP),
    ('project:update', '修改项目（草稿或退回状态）', CURRENT_TIMESTAMP),
    ('project:delete', '删除项目草稿', CURRENT_TIMESTAMP),
    ('project:submit', '提交立项申请', CURRENT_TIMESTAMP),
    ('project:approve', '审批立项（含驳回）', CURRENT_TIMESTAMP),
    ('project:close-request', '申请项目结项', CURRENT_TIMESTAMP),
    ('project:close-review', '审批项目结项', CURRENT_TIMESTAMP),
    ('project:member:manage', '管理项目成员', CURRENT_TIMESTAMP),
    ('task:create', '创建并分派任务', CURRENT_TIMESTAMP),
    ('task:read', '查看项目任务', CURRENT_TIMESTAMP),
    ('task:update', '修改或取消任务', CURRENT_TIMESTAMP),
    ('task:delete', '删除未开始的草稿任务', CURRENT_TIMESTAMP),
    ('task:start', '开始任务', CURRENT_TIMESTAMP),
    ('task:submit', '提交任务交付', CURRENT_TIMESTAMP),
    ('task:review', '验收或退回任务', CURRENT_TIMESTAMP),
    ('report:create', '创建周报草稿', CURRENT_TIMESTAMP),
    ('report:read', '查看授权范围内的周报', CURRENT_TIMESTAMP),
    ('report:update', '修改本人周报草稿', CURRENT_TIMESTAMP),
    ('report:delete', '删除本人周报草稿', CURRENT_TIMESTAMP),
    ('report:submit', '提交周报', CURRENT_TIMESTAMP),
    ('report:review', '审核或退回周报', CURRENT_TIMESTAMP),
    ('resource:manage', '登记与维护实验室资源', CURRENT_TIMESTAMP),
    ('resource:read', '查看实验室资源', CURRENT_TIMESTAMP),
    ('booking:create', '申请资源预约', CURRENT_TIMESTAMP),
    ('booking:read', '查看资源预约', CURRENT_TIMESTAMP),
    ('booking:cancel', '取消本人预约', CURRENT_TIMESTAMP),
    ('booking:review', '审批资源预约', CURRENT_TIMESTAMP),
    ('artifact:create', '登记科研成果', CURRENT_TIMESTAMP),
    ('artifact:read', '查看科研成果', CURRENT_TIMESTAMP),
    ('artifact:update', '修改本人成果草稿', CURRENT_TIMESTAMP),
    ('artifact:delete', '删除本人成果草稿', CURRENT_TIMESTAMP),
    ('artifact:submit', '提交成果审核', CURRENT_TIMESTAMP),
    ('artifact:review', '审核科研成果', CURRENT_TIMESTAMP),
    ('knowledge:create', '创建知识条目', CURRENT_TIMESTAMP),
    ('knowledge:read', '检索知识条目', CURRENT_TIMESTAMP),
    ('knowledge:update', '修改本人知识条目', CURRENT_TIMESTAMP),
    ('knowledge:delete', '删除本人知识草稿', CURRENT_TIMESTAMP),
    ('knowledge:publish', '发布知识条目', CURRENT_TIMESTAMP),
    ('notification:read', '查看本人通知', CURRENT_TIMESTAMP),
    ('notification:mark', '标记通知已读', CURRENT_TIMESTAMP),
    ('ai:report:generate', '使用授权数据生成周报草稿', CURRENT_TIMESTAMP),
    ('audit:read', '查看脱敏审计记录', CURRENT_TIMESTAMP);

-- ---------------------------------------------------------------------
-- 角色 × 权限（默认矩阵，管理员可在后台动态调整）
-- ---------------------------------------------------------------------
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code = 'SYS_ADMIN'
  AND p.code IN ('user:manage', 'role:manage', 'lab:create', 'lab:appoint',
                 'notification:read', 'notification:mark', 'audit:read');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code = 'PI'
  AND p.code IN ('lab:read', 'lab:member:manage', 'project:read', 'project:approve', 'project:close-review',
                 'task:read', 'report:read', 'report:review',
                 'resource:manage', 'resource:read', 'booking:read', 'booking:review',
                 'artifact:read', 'artifact:review', 'knowledge:read',
                 'notification:read', 'notification:mark', 'ai:report:generate', 'audit:read');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code = 'PROJECT_LEADER'
  AND p.code IN ('lab:read', 'project:create', 'project:read', 'project:update', 'project:delete',
                 'project:submit', 'project:close-request', 'project:member:manage',
                 'task:create', 'task:read', 'task:update', 'task:delete', 'task:start', 'task:submit', 'task:review',
                 'report:create', 'report:read', 'report:update', 'report:delete', 'report:submit', 'report:review',
                 'booking:create', 'booking:read', 'booking:cancel',
                 'artifact:create', 'artifact:read', 'artifact:update', 'artifact:delete', 'artifact:submit',
                 'artifact:review', 'knowledge:create', 'knowledge:read', 'knowledge:update', 'knowledge:delete',
                 'knowledge:publish', 'notification:read', 'notification:mark', 'ai:report:generate');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code = 'RESEARCHER'
  AND p.code IN ('lab:read', 'project:read', 'task:read', 'task:start', 'task:submit',
                 'report:create', 'report:read', 'report:update', 'report:delete', 'report:submit',
                 'booking:create', 'booking:read', 'booking:cancel',
                 'artifact:create', 'artifact:read', 'artifact:update', 'artifact:delete', 'artifact:submit',
                 'knowledge:create', 'knowledge:read', 'knowledge:update', 'knowledge:delete', 'knowledge:publish',
                 'notification:read', 'notification:mark', 'ai:report:generate');

-- ---------------------------------------------------------------------
-- 演示账号（虚构；统一演示口令 Labflow@2026，README 中列出）
-- ---------------------------------------------------------------------
INSERT INTO users (username, password_hash, email, display_name, enabled, auth_version, created_at, updated_at) VALUES
    ('admin_demo', '$2a$10$pD6CkUgmYNLTLqymNCOB/ujxhj6Hs3.mONUXjlW0cnYaTouUjBHtO',
     'admin_demo@labflow.test', '系统管理员', TRUE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('pi_demo', '$2a$10$3nZ9bGoYIe05jBbzvzzuduaRjyXLSe/TKZ1u.lomcY./fgZ44k4MG',
     'pi_demo@labflow.test', '林之远（PI）', TRUE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('leader_demo', '$2a$10$6ZTCFeAbWZ0aWLSP7MlkOeGPpD/Q46I/TH0pD2iW2nCa/aH8sYjtK',
     'leader_demo@labflow.test', '陈行（项目负责人）', TRUE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('researcher_a', '$2a$10$mXMiZBJupwnXesO4.fOKMuwtl2.jJJC2r8MrLYiPTQIXxqUQCfX66',
     'researcher_a@labflow.test', '苏禾（科研人员A）', TRUE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('researcher_b', '$2a$10$d1gt9wAR0xuHobxZ0oW2YeaH35WY8UhkJX1hy6pNMgvEpCQJiu0KG',
     'researcher_b@labflow.test', '周昀（科研人员B）', TRUE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u, roles r WHERE u.username = 'admin_demo' AND r.code = 'SYS_ADMIN';

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u, roles r WHERE u.username = 'pi_demo' AND r.code = 'PI';

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u, roles r WHERE u.username = 'leader_demo' AND r.code = 'PROJECT_LEADER';

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u, roles r
WHERE u.username IN ('researcher_a', 'researcher_b') AND r.code = 'RESEARCHER';

-- ---------------------------------------------------------------------
-- 实验室与成员关系（作用域基础：角色决定能力，成员关系决定范围）
-- ---------------------------------------------------------------------
INSERT INTO labs (code, name, description, enabled, version, created_at, updated_at) VALUES
    ('LAB-A', '智能感知实验室', '演示用实验室 A：项目、任务、周报主流程', TRUE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('LAB-B', '数据智能实验室', '演示用实验室 B：跨实验室越权负例', TRUE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO lab_members (lab_id, user_id, member_type, active, joined_at)
SELECT l.id, u.id, 'PI', TRUE, CURRENT_TIMESTAMP FROM labs l, users u
WHERE l.code = 'LAB-A' AND u.username = 'pi_demo';

INSERT INTO lab_members (lab_id, user_id, member_type, active, joined_at)
SELECT l.id, u.id, 'MEMBER', TRUE, CURRENT_TIMESTAMP FROM labs l, users u
WHERE l.code = 'LAB-A' AND u.username IN ('leader_demo', 'researcher_a');

INSERT INTO lab_members (lab_id, user_id, member_type, active, joined_at)
SELECT l.id, u.id, 'MEMBER', TRUE, CURRENT_TIMESTAMP FROM labs l, users u
WHERE l.code = 'LAB-A' AND u.username = 'admin_demo';

INSERT INTO lab_members (lab_id, user_id, member_type, active, joined_at)
SELECT l.id, u.id, 'MEMBER', TRUE, CURRENT_TIMESTAMP FROM labs l, users u
WHERE l.code = 'LAB-B' AND u.username = 'researcher_b';
