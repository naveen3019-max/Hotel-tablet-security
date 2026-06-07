from datetime import datetime, timedelta
from typing import Optional
from jose import JWTError, jwt
from passlib.context import CryptContext
from fastapi import HTTPException, Security
from fastapi.security import HTTPBearer, HTTPAuthorizationCredentials
from config import settings
import logging

logger = logging.getLogger(__name__)

pwd_context = CryptContext(schemes=["bcrypt"], deprecated="auto")
security = HTTPBearer()

class AuthService:
    """JWT Authentication Service"""
    
    @staticmethod
    def create_access_token(data: dict, expires_delta: Optional[timedelta] = None) -> str:
        """Create JWT access token"""
        to_encode = data.copy()
        
        if expires_delta:
            expire = datetime.utcnow() + expires_delta
        else:
            expire = datetime.utcnow() + timedelta(minutes=settings.jwt_expiration_minutes)
        
        to_encode.update({"exp": expire, "iat": datetime.utcnow()})
        
        encoded_jwt = jwt.encode(
            to_encode,
            settings.secret_key,
            algorithm=settings.jwt_algorithm
        )
        
        return encoded_jwt
    
    @staticmethod
    def verify_token(token: str) -> dict:
        """Verify and decode JWT token"""
        try:
            payload = jwt.decode(
                token,
                settings.secret_key,
                algorithms=[settings.jwt_algorithm]
            )
            return payload
        except JWTError as e:
            logger.error(f"JWT verification failed: {e}")
            raise HTTPException(status_code=401, detail="Invalid authentication credentials")
    
    @staticmethod
    def create_device_token(device_id: str, room_id: str, hotel_id: str = "default") -> str:
        """Create device-specific JWT token"""
        return AuthService.create_access_token(
            data={
                "sub": device_id,
                "room_id": room_id,
                "hotel_id": hotel_id,
                "type": "device"
            }
        )
    
    @staticmethod
    def create_user_token(user_id: str, role: str) -> str:
        """Create user JWT token"""
        return AuthService.create_access_token(
            data={
                "sub": user_id,
                "role": role,
                "type": "user"
            }
        )
    
    @staticmethod
    def hash_password(password: str) -> str:
        """Hash password using bcrypt"""
        return pwd_context.hash(password)
    
    @staticmethod
    def verify_password(plain_password: str, hashed_password: str) -> bool:
        """Verify password against hash"""
        return pwd_context.verify(plain_password, hashed_password)

def get_current_token(credentials: HTTPAuthorizationCredentials = Security(security)) -> dict:
    """Dependency to get current JWT token"""
    token = credentials.credentials
    return AuthService.verify_token(token)

def get_current_device(token_data: dict = Security(get_current_token)) -> str:
    """Dependency to get current device ID from token"""
    if token_data.get("type") != "device":
        raise HTTPException(status_code=403, detail="Device token required")
    
    device_id = token_data.get("sub")
    if not device_id:
        raise HTTPException(status_code=401, detail="Invalid device token")
    
    return device_id

def get_current_user(token_data: dict = Security(get_current_token)) -> dict:
    """Dependency to get current user from token"""
    if token_data.get("type") != "user":
        raise HTTPException(status_code=403, detail="User token required")
    
    user_id = token_data.get("sub")
    role = token_data.get("role")
    
    if not user_id:
        raise HTTPException(status_code=401, detail="Invalid user token")
    
    return {"user_id": user_id, "role": role}

def require_role(required_role: str):
    """Dependency factory to require specific role"""
    def role_checker(user: dict = Security(get_current_user)) -> dict:
        if user["role"] != required_role and user["role"] != "admin":
            raise HTTPException(status_code=403, detail=f"Role '{required_role}' required")
        return user
    return role_checker
