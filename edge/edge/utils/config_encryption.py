"""
Configuration encryption utilities for sensitive data.

Provides AES-256-GCM encryption for sensitive configuration values like
RTSP credentials, API keys, and other secrets at rest.
"""

import base64
import os
from cryptography.fernet import Fernet
from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives.kdf.pbkdf2 import PBKDF2HMAC
import structlog
from typing import Optional, Dict, Any
import json

logger = structlog.get_logger(__name__)


class ConfigEncryption:
    """
    AES-256-GCM encryption for sensitive configuration values.
    
    Uses PBKDF2 key derivation with a salt for strong encryption keys.
    Stores encrypted values as base64-encoded strings for easy serialization.
    """
    
    def __init__(self, password: Optional[str] = None, salt: Optional[bytes] = None):
        """
        Initialize encryption with password and salt.
        
        Args:
            password: Encryption password. If None, attempts to get from ENV
            salt: Salt for key derivation. If None, generates or gets from ENV
        """
        self.password = password or os.getenv("EDGE_CONFIG_PASSWORD")
        if not self.password:
            raise ValueError("Encryption password required. Set EDGE_CONFIG_PASSWORD environment variable.")
        
        # Use provided salt or try to get from environment
        if salt:
            self.salt = salt
        else:
            salt_b64 = os.getenv("EDGE_CONFIG_SALT")
            if salt_b64:
                self.salt = base64.b64decode(salt_b64.encode())
            else:
                # Generate new salt and save to environment (for development)
                self.salt = os.urandom(16)
                logger.warning("Generated new encryption salt. Set EDGE_CONFIG_SALT environment variable for production.")
                print(f"EDGE_CONFIG_SALT={base64.b64encode(self.salt).decode()}")
        
        # Derive encryption key
        kdf = PBKDF2HMAC(
            algorithm=hashes.SHA256(),
            length=32,
            salt=self.salt,
            iterations=100000,  # NIST recommended minimum
        )
        key = base64.urlsafe_b64encode(kdf.derive(self.password.encode()))
        self.fernet = Fernet(key)
        
        logger.info("Configuration encryption initialized")
    
    def encrypt_value(self, plaintext: str) -> str:
        """
        Encrypt a string value.
        
        Args:
            plaintext: String to encrypt
            
        Returns:
            Base64-encoded encrypted string
        """
        if not plaintext:
            return plaintext
        
        try:
            encrypted_bytes = self.fernet.encrypt(plaintext.encode())
            return base64.b64encode(encrypted_bytes).decode()
        except Exception as e:
            logger.error("Failed to encrypt value", error=str(e))
            raise
    
    def decrypt_value(self, encrypted_b64: str) -> str:
        """
        Decrypt a base64-encoded encrypted string.
        
        Args:
            encrypted_b64: Base64-encoded encrypted string
            
        Returns:
            Decrypted plaintext string
        """
        if not encrypted_b64:
            return encrypted_b64
        
        try:
            encrypted_bytes = base64.b64decode(encrypted_b64.encode())
            decrypted_bytes = self.fernet.decrypt(encrypted_bytes)
            return decrypted_bytes.decode()
        except Exception as e:
            logger.error("Failed to decrypt value", error=str(e))
            raise
    
    def encrypt_rtsp_url(self, rtsp_url: str) -> str:
        """
        Encrypt an RTSP URL, preserving the basic structure for validation.
        
        Only encrypts the credentials portion (username:password) while keeping
        the protocol and host information accessible for connection logic.
        
        Args:
            rtsp_url: RTSP URL potentially containing credentials
            
        Returns:
            RTSP URL with encrypted credentials
        """
        if not rtsp_url or not rtsp_url.startswith('rtsp://'):
            return rtsp_url
        
        try:
            # Parse RTSP URL to extract credentials
            # Format: rtsp://username:password@host:port/path
            url_parts = rtsp_url[7:]  # Remove 'rtsp://'
            
            if '@' not in url_parts:
                # No credentials to encrypt
                return rtsp_url
            
            credentials_part, host_part = url_parts.split('@', 1)
            
            if ':' in credentials_part:
                # Encrypt the credentials part
                encrypted_credentials = self.encrypt_value(credentials_part)
                return f"rtsp://encrypted:{encrypted_credentials}@{host_part}"
            else:
                # Only username, no password - encrypt anyway for consistency
                encrypted_credentials = self.encrypt_value(credentials_part)
                return f"rtsp://encrypted:{encrypted_credentials}@{host_part}"
                
        except Exception as e:
            logger.error("Failed to encrypt RTSP URL", error=str(e))
            return rtsp_url
    
    def decrypt_rtsp_url(self, encrypted_rtsp_url: str) -> str:
        """
        Decrypt an RTSP URL that has encrypted credentials.
        
        Args:
            encrypted_rtsp_url: RTSP URL with encrypted credentials
            
        Returns:
            RTSP URL with decrypted credentials
        """
        if not encrypted_rtsp_url or not encrypted_rtsp_url.startswith('rtsp://'):
            return encrypted_rtsp_url
        
        try:
            # Parse encrypted RTSP URL
            url_parts = encrypted_rtsp_url[7:]  # Remove 'rtsp://'
            
            if '@' not in url_parts or not url_parts.startswith('encrypted:'):
                # Not encrypted or no credentials
                return encrypted_rtsp_url
            
            # Extract encrypted credentials
            encrypted_part, host_part = url_parts.split('@', 1)
            encrypted_credentials = encrypted_part[10:]  # Remove 'encrypted:' prefix
            
            # Decrypt credentials
            decrypted_credentials = self.decrypt_value(encrypted_credentials)
            
            return f"rtsp://{decrypted_credentials}@{host_part}"
            
        except Exception as e:
            logger.error("Failed to decrypt RTSP URL", error=str(e))
            return encrypted_rtsp_url
    
    def encrypt_config_dict(self, config: Dict[str, Any], sensitive_keys: set = None) -> Dict[str, Any]:
        """
        Encrypt sensitive values in a configuration dictionary.
        
        Args:
            config: Configuration dictionary
            sensitive_keys: Set of keys that contain sensitive data
            
        Returns:
            Configuration dictionary with encrypted sensitive values
        """
        if sensitive_keys is None:
            sensitive_keys = {
                'rtsp_url', 'video_source', 'backend_auth_token', 
                'minio_access_key', 'minio_secret_key', 'api_key',
                'password', 'secret', 'token', 'key'
            }
        
        encrypted_config = config.copy()
        
        for key, value in config.items():
            if isinstance(value, str) and any(sensitive_key in key.lower() for sensitive_key in sensitive_keys):
                if key.lower() in ['rtsp_url', 'video_source'] and value.startswith('rtsp://'):
                    encrypted_config[key] = self.encrypt_rtsp_url(value)
                else:
                    encrypted_config[key] = self.encrypt_value(value)
                    
        return encrypted_config
    
    def decrypt_config_dict(self, encrypted_config: Dict[str, Any], sensitive_keys: set = None) -> Dict[str, Any]:
        """
        Decrypt sensitive values in a configuration dictionary.
        
        Args:
            encrypted_config: Configuration dictionary with encrypted values
            sensitive_keys: Set of keys that contain sensitive data
            
        Returns:
            Configuration dictionary with decrypted sensitive values
        """
        if sensitive_keys is None:
            sensitive_keys = {
                'rtsp_url', 'video_source', 'backend_auth_token',
                'minio_access_key', 'minio_secret_key', 'api_key',
                'password', 'secret', 'token', 'key'
            }
        
        decrypted_config = encrypted_config.copy()
        
        for key, value in encrypted_config.items():
            if isinstance(value, str) and any(sensitive_key in key.lower() for sensitive_key in sensitive_keys):
                if key.lower() in ['rtsp_url', 'video_source'] and value.startswith('rtsp://encrypted:'):
                    decrypted_config[key] = self.decrypt_rtsp_url(value)
                else:
                    try:
                        # Try to decrypt - if it fails, assume it's not encrypted
                        decrypted_config[key] = self.decrypt_value(value)
                    except Exception:
                        # Not encrypted or invalid encryption - keep original value
                        decrypted_config[key] = value
        
        return decrypted_config


def generate_salt() -> str:
    """Generate a new base64-encoded salt for encryption."""
    salt = os.urandom(16)
    return base64.b64encode(salt).decode()


def encrypt_config_file(input_path: str, output_path: str, password: str, salt: Optional[str] = None) -> None:
    """
    Encrypt sensitive values in a JSON configuration file.
    
    Args:
        input_path: Path to input configuration file
        output_path: Path to output encrypted configuration file  
        password: Encryption password
        salt: Base64-encoded salt (generates new one if None)
    """
    # Load configuration
    with open(input_path, 'r') as f:
        config = json.load(f)
    
    # Setup encryption
    salt_bytes = base64.b64decode(salt.encode()) if salt else None
    encryptor = ConfigEncryption(password=password, salt=salt_bytes)
    
    # Encrypt sensitive values
    encrypted_config = encryptor.encrypt_config_dict(config)
    
    # Save encrypted configuration
    with open(output_path, 'w') as f:
        json.dump(encrypted_config, f, indent=2)
    
    logger.info("Configuration file encrypted", 
                input=input_path, output=output_path)


def decrypt_config_file(input_path: str, output_path: str, password: str, salt: str) -> None:
    """
    Decrypt sensitive values in a JSON configuration file.
    
    Args:
        input_path: Path to encrypted configuration file
        output_path: Path to output decrypted configuration file
        password: Encryption password
        salt: Base64-encoded salt
    """
    # Load encrypted configuration
    with open(input_path, 'r') as f:
        encrypted_config = json.load(f)
    
    # Setup decryption
    salt_bytes = base64.b64decode(salt.encode())
    decryptor = ConfigEncryption(password=password, salt=salt_bytes)
    
    # Decrypt sensitive values
    decrypted_config = decryptor.decrypt_config_dict(encrypted_config)
    
    # Save decrypted configuration  
    with open(output_path, 'w') as f:
        json.dump(decrypted_config, f, indent=2)
    
    logger.info("Configuration file decrypted",
                input=input_path, output=output_path)


# CLI interface for configuration encryption
if __name__ == "__main__":
    import argparse
    
    parser = argparse.ArgumentParser(description="Encrypt/decrypt edge configuration files")
    parser.add_argument("--encrypt", action="store_true", help="Encrypt configuration")
    parser.add_argument("--decrypt", action="store_true", help="Decrypt configuration")
    parser.add_argument("--input", required=True, help="Input configuration file")
    parser.add_argument("--output", required=True, help="Output configuration file")
    parser.add_argument("--password", help="Encryption password (or set EDGE_CONFIG_PASSWORD)")
    parser.add_argument("--salt", help="Base64-encoded salt (generates new one if not provided)")
    parser.add_argument("--generate-salt", action="store_true", help="Generate a new salt")
    
    args = parser.parse_args()
    
    if args.generate_salt:
        print(f"Generated salt: {generate_salt()}")
        exit(0)
    
    if args.encrypt == args.decrypt:
        print("Specify either --encrypt or --decrypt")
        exit(1)
    
    password = args.password or os.getenv("EDGE_CONFIG_PASSWORD")
    if not password:
        print("Password required. Use --password or set EDGE_CONFIG_PASSWORD environment variable.")
        exit(1)
    
    if args.encrypt:
        encrypt_config_file(args.input, args.output, password, args.salt)
        print(f"Configuration encrypted: {args.input} -> {args.output}")
    else:
        if not args.salt:
            print("Salt required for decryption. Use --salt argument.")
            exit(1)
        decrypt_config_file(args.input, args.output, password, args.salt)
        print(f"Configuration decrypted: {args.input} -> {args.output}")
