"""
Python AI 微服务主入口（FastAPI，端口 9094）

提供三个 AI 计算端点：
  POST /ml/knn           - 增强版 KNN 志愿者匹配（TF-IDF + 服务时长奖励）
  POST /ml/churn         - 志愿者流失预警评分
  POST /ai/audit/organizer - 组织者入驻 AI 尽调
  POST /ai/audit/activity  - 活动发布 AI 风控初审
  POST /ai/recommend       - 双阶段复合推荐引擎（Transformer + DeepSeek）
  GET  /health           - 健康检查
"""
import os
import pathlib

# ── 启动时自动加载 .env（不依赖 python-dotenv） ──────────────────────
_env_path = pathlib.Path(__file__).parent / ".env"
if _env_path.exists():
    for _line in _env_path.read_text(encoding="utf-8").splitlines():
        _line = _line.strip()
        if _line and not _line.startswith("#") and "=" in _line:
            _k, _v = _line.split("=", 1)
            os.environ.setdefault(_k.strip(), _v.strip())

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from typing import List, Optional, Dict, Any
import uvicorn
import logging
import threading

from knn_service import knn_match
from churn_service import predict_churn
from audit_service import audit_organizer, audit_activity
from recommend_service import hybrid_recommend
from feed_service import feed_recommend

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("python-ai")

app = FastAPI(title="智能志愿者 Python AI 微服务", version="2.0.0")


# ─────────────────────────── 数据模型 ────────────────────────────────

class VolunteerProfile(BaseModel):
    """KNN 接口：单个志愿者档案"""
    userId: int
    realName: Optional[str] = None
    skills: Optional[str] = None        # 逗号分隔，如 "急救,医疗"
    total_hours: Optional[int] = 0
    credit_balance: Optional[int] = 0   # 积分余额（信誉权重）
    attendance_rate: Optional[float] = 0.0  # 出勤率 0.0~1.0


class KnnRequest(BaseModel):
    requiredSkills: List[str]          # 活动所需技能
    topK: int = 5
    volunteers: List[VolunteerProfile]


class VolunteerStats(BaseModel):
    """Churn 接口：单个志愿者统计数据"""
    user_id: int
    nickname: Optional[str] = None
    signup_count: int = 0
    cancel_count: int = 0
    total_hours: int = 0
    inactive_days: int = 0             # 距最近活动天数


class ChurnRequest(BaseModel):
    volunteers: List[VolunteerStats]


# 组织者入驻 AI 尽调请求
class OrganizerAuditRequest(BaseModel):
    user_id: int
    username: str
    nickname: Optional[str] = None
    apply_reason: str
    total_hours: int = 0
    total_activities: int = 0
    signup_count: int = 0
    cancel_count: int = 0
    skills: Optional[str] = None
    credit_balance: int = 0


# 活动发布 AI 风控初审请求
class ActivityAuditRequest(BaseModel):
    activity_id: int
    title: str
    description: Optional[str] = ""
    organizer_name: Optional[str] = None


# 双阶段推荐请求
class RecommendRequest(BaseModel):
    user_id: int
    user_skills: Optional[List[str]] = []
    user_profile: Optional[str] = ""
    required_skills: List[str]
    activity_title: str
    activity_description: Optional[str] = ""
    candidates: List[Any]
    top_k: int = 3


# Feed 推荐请求
class HistoryActivity(BaseModel):
    title: Optional[str] = ""
    description: Optional[str] = ""
    behavior_type: str = "registered"   # completed | checked_in | registered

class FeedRequest(BaseModel):
    user_id: int
    user_skills: Optional[List[str]] = []
    user_profile: Optional[str] = ""
    history_activities: Optional[List[HistoryActivity]] = []
    activity_pool: List[Any]
    page: int = 1
    page_size: int = 6


# ─────────────────────────── 路由 ────────────────────────────────────

@app.get("/health")
def health():
    return {"status": "UP", "service": "python-ai", "port": 9094}


@app.post("/ml/knn")
def knn_endpoint(req: KnnRequest):
    """
    增强版 KNN 志愿者匹配

    相比 Java 版改进：
    - TF-IDF 加权（稀缺技能权重更高）
    - 服务时长奖励分（经验更丰富优先）
    - 批量矩阵计算，性能更优
    """
    logger.info(f"KNN request: skills={req.requiredSkills}, topK={req.topK}, volunteers={len(req.volunteers)}")
    try:
        vols = [v.dict() for v in req.volunteers]
        result = knn_match(req.requiredSkills, vols, req.topK)
        return {"code": 200, "msg": "success", "data": result}
    except Exception as e:
        logger.error(f"KNN error: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail=str(e))


@app.post("/ml/churn")
def churn_endpoint(req: ChurnRequest):
    """
    志愿者流失预警评分

    模型特征：取消率、不活跃天数、服务时长、报名次数
    输出：风险评分(0~1) + 风险等级(高/中/低) + 风险因素说明
    """
    logger.info(f"Churn request: {len(req.volunteers)} volunteers")
    try:
        vols = [v.dict() for v in req.volunteers]
        result = predict_churn(vols)
        return {"code": 200, "msg": "success", "data": result}
    except Exception as e:
        logger.error(f"Churn error: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail=str(e))


@app.post("/ai/audit/organizer")
def organizer_audit_endpoint(req: OrganizerAuditRequest):
    """
    组织者入驻 AI 尽调

    分析志愿者的历史履约数据和申请理由，输出维度分析和结论。
    """
    logger.info(f"Organizer audit request: user_id={req.user_id}")
    try:
        data = req.dict()
        result = audit_organizer(data)
        return {"code": 200, "msg": "success", "data": result}
    except Exception as e:
        logger.error(f"Organizer audit error: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail=str(e))


@app.post("/ai/audit/activity")
def activity_audit_endpoint(req: ActivityAuditRequest):
    """
    活动发布 AI 风控初审

    使用 DeepSeek 检查活动标题和详情是否包含违规/涉黄/营销内容。
    """
    logger.info(f"Activity audit request: activity_id={req.activity_id}, title={req.title}")
    try:
        data = req.dict()
        result = audit_activity(data)
        return {"code": 200, "msg": "success", "data": result}
    except Exception as e:
        logger.error(f"Activity audit error: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail=str(e))


@app.post("/ai/feed")
def feed_endpoint(req: FeedRequest):
    """
    动态隐式反馈个性化 Feed 推荐

    阶段一：Transformer 语义向量融合（画像 + 行为历史加权）
    阶段二：全局余弦相似度排序 + 分页
    阶段三：DeepSeek 批量生成 ≤30 字专属推荐语
    """
    logger.info(f"Feed request: user_id={req.user_id}, pool={len(req.activity_pool)}, page={req.page}")
    try:
        data = {
            "user_id":           req.user_id,
            "user_skills":       req.user_skills or [],
            "user_profile":      req.user_profile or "",
            "history_activities": [h.dict() for h in (req.history_activities or [])],
            "activity_pool":     [a if isinstance(a, dict) else a.dict() for a in req.activity_pool],
            "page":              req.page,
            "page_size":         req.page_size
        }
        result = feed_recommend(data)
        return {"code": 200, "msg": "success", "data": result}
    except Exception as e:
        logger.error(f"Feed error: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail=str(e))


@app.post("/ai/recommend")
def recommend_endpoint(req: RecommendRequest):
    """
    双阶段复合推荐引擎

    阶段一（召回）：sentence-transformers 向量匹配，截取 Top-3
    阶段二（精排）：DeepSeek Agent 选出最合适的人选并输出推荐理由
    """
    logger.info(f"Recommend request: user_id={req.user_id}, activity={req.activity_title}, candidates={len(req.candidates)}")
    try:
        data = {
            "user_id": req.user_id,
            "user_skills": req.user_skills or [],
            "user_profile": req.user_profile or "",
            "required_skills": req.required_skills,
            "activity_title": req.activity_title,
            "activity_description": req.activity_description or "",
            "candidates": [c if isinstance(c, dict) else c.dict() for c in req.candidates],
            "top_k": req.top_k
        }
        result = hybrid_recommend(data)
        return {"code": 200, "msg": "success", "data": result}
    except Exception as e:
        logger.error(f"Recommend error: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail=str(e))


@app.on_event("startup")
async def warmup_models():
    """应用启动时预热 Transformer 模型，消除首次调用的冷启动延迟"""
    import asyncio
    loop = asyncio.get_event_loop()
    def _warmup():
        try:
            from recommend_service import _get_transformer
            logger.info("正在预热 Transformer 模型（text2vec-base-chinese）...")
            _get_transformer()
            logger.info("Transformer 模型预热完成，后续推荐请求将立即响应")
        except Exception as e:
            logger.warning(f"模型预热失败（不影响服务启动）: {e}")
    loop.run_in_executor(None, _warmup)


if __name__ == "__main__":
    uvicorn.run("main:app", host="0.0.0.0", port=9094, reload=False)
