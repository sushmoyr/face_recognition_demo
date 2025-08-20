"""
Tests for configuration encryption utilities.

Tests RTSP URL encryption, general value encryption, and configuration
dictionary encryption/decryption.
"""

import os
import tempfile
import json
from unittest.mock import patch
import pytest
from cryptography.fernet import Fernet

# Test imports
from edge.utils.config_encryption import (
    ConfigEncryption, 
    generate_salt,
    encrypt_config_file,
    decrypt_config_file
)


class TestConfigEncryption:
    """Test configuration encryption functionality."""
    
    def setup_method(self):
        """Setup test environment."""
        self.test_password = "test-password-123"
        self.test_salt = b"1234567890123456"  # 16 bytes for testing
        self.encryptor = ConfigEncryption(password=self.test_password, salt=self.test_salt)
    
    def test_encrypt_decrypt_value(self):
        """Test basic value encryption and decryption."""
        plaintext = "sensitive-value-123"
        
        # Encrypt
        encrypted = self.encryptor.encrypt_value(plaintext)
        assert encrypted != plaintext
        assert len(encrypted) > 0
        
        # Decrypt
        decrypted = self.encryptor.decrypt_value(encrypted)
        assert decrypted == plaintext
    
    def test_encrypt_decrypt_empty_value(self):
        """Test encryption of empty/None values."""
        assert self.encryptor.encrypt_value("") == ""
        assert self.encryptor.encrypt_value(None) is None
        assert self.encryptor.decrypt_value("") == ""
        assert self.encryptor.decrypt_value(None) is None
    
    def test_rtsp_url_encryption_with_credentials(self):
        """Test RTSP URL encryption with username and password."""
        rtsp_url = "rtsp://admin:password123@192.168.1.100:554/stream1"
        
        # Encrypt
        encrypted_url = self.encryptor.encrypt_rtsp_url(rtsp_url)
        assert encrypted_url.startswith("rtsp://encrypted:")
        assert "@192.168.1.100:554/stream1" in encrypted_url
        assert "admin:password123" not in encrypted_url
        
        # Decrypt
        decrypted_url = self.encryptor.decrypt_rtsp_url(encrypted_url)
        assert decrypted_url == rtsp_url
    
    def test_rtsp_url_encryption_username_only(self):
        """Test RTSP URL encryption with username only."""
        rtsp_url = "rtsp://admin@192.168.1.100:554/stream1"
        
        # Encrypt
        encrypted_url = self.encryptor.encrypt_rtsp_url(rtsp_url)
        assert encrypted_url.startswith("rtsp://encrypted:")
        assert "@192.168.1.100:554/stream1" in encrypted_url
        
        # Decrypt
        decrypted_url = self.encryptor.decrypt_rtsp_url(encrypted_url)
        assert decrypted_url == rtsp_url
    
    def test_rtsp_url_no_credentials(self):
        """Test RTSP URL without credentials (should remain unchanged)."""
        rtsp_url = "rtsp://192.168.1.100:554/stream1"
        
        # Encrypt (should do nothing)
        encrypted_url = self.encryptor.encrypt_rtsp_url(rtsp_url)
        assert encrypted_url == rtsp_url
        
        # Decrypt (should do nothing)  
        decrypted_url = self.encryptor.decrypt_rtsp_url(encrypted_url)
        assert decrypted_url == rtsp_url
    
    def test_non_rtsp_url(self):
        """Test with non-RTSP URLs."""
        http_url = "http://example.com"
        
        # Should remain unchanged
        assert self.encryptor.encrypt_rtsp_url(http_url) == http_url
        assert self.encryptor.decrypt_rtsp_url(http_url) == http_url
    
    def test_encrypt_config_dict(self):
        """Test configuration dictionary encryption."""
        config = {
            "rtsp_url": "rtsp://admin:pass@192.168.1.100/stream",
            "minio_access_key": "minioaccess",
            "minio_secret_key": "miniosecret",
            "backend_auth_token": "Bearer token123",
            "device_id": "edge-001",  # Should not be encrypted
            "target_fps": 10,  # Should not be encrypted
        }
        
        encrypted_config = self.encryptor.encrypt_config_dict(config)
        
        # Sensitive keys should be encrypted
        assert encrypted_config["rtsp_url"].startswith("rtsp://encrypted:")
        assert encrypted_config["minio_access_key"] != "minioaccess"
        assert encrypted_config["minio_secret_key"] != "miniosecret"
        assert encrypted_config["backend_auth_token"] != "Bearer token123"
        
        # Non-sensitive keys should remain unchanged
        assert encrypted_config["device_id"] == "edge-001"
        assert encrypted_config["target_fps"] == 10
        
        # Decrypt and verify
        decrypted_config = self.encryptor.decrypt_config_dict(encrypted_config)
        assert decrypted_config == config
    
    def test_custom_sensitive_keys(self):
        """Test encryption with custom sensitive keys."""
        config = {
            "my_secret": "secret123",
            "public_value": "public",
            "another_secret": "secret456"
        }
        
        sensitive_keys = {"my_secret", "another_secret"}
        
        encrypted_config = self.encryptor.encrypt_config_dict(config, sensitive_keys)
        
        assert encrypted_config["my_secret"] != "secret123"
        assert encrypted_config["another_secret"] != "secret456"
        assert encrypted_config["public_value"] == "public"
        
        decrypted_config = self.encryptor.decrypt_config_dict(encrypted_config, sensitive_keys)
        assert decrypted_config == config
    
    def test_encryption_consistency(self):
        """Test that encryption is consistent across instances with same key."""
        encryptor2 = ConfigEncryption(password=self.test_password, salt=self.test_salt)
        
        plaintext = "test-value"
        encrypted1 = self.encryptor.encrypt_value(plaintext)
        encrypted2 = encryptor2.encrypt_value(plaintext)
        
        # Note: Fernet includes timestamp, so encrypted values will differ
        # but both should decrypt to the same plaintext
        assert self.encryptor.decrypt_value(encrypted2) == plaintext
        assert encryptor2.decrypt_value(encrypted1) == plaintext
    
    def test_invalid_decryption(self):
        """Test handling of invalid encrypted values."""
        with pytest.raises(Exception):
            self.encryptor.decrypt_value("invalid-encrypted-data")
    
    def test_decrypt_config_dict_with_unencrypted_values(self):
        """Test that decryption handles mixed encrypted/unencrypted values."""
        config = {
            "encrypted_value": self.encryptor.encrypt_value("secret"),
            "plain_value": "not-encrypted",
            "api_key": "plain-api-key"  # Matches sensitive key but not encrypted
        }
        
        # Should handle mixed values gracefully
        decrypted_config = self.encryptor.decrypt_config_dict(config)
        
        assert decrypted_config["encrypted_value"] == "secret"
        assert decrypted_config["plain_value"] == "not-encrypted"
        assert decrypted_config["api_key"] == "plain-api-key"


class TestConfigEncryptionEnvironment:
    """Test configuration encryption with environment variables."""
    
    @patch.dict(os.environ, {"EDGE_CONFIG_PASSWORD": "env-password"})
    def test_password_from_environment(self):
        """Test loading password from environment variable."""
        encryptor = ConfigEncryption()
        
        plaintext = "test"
        encrypted = encryptor.encrypt_value(plaintext)
        decrypted = encryptor.decrypt_value(encrypted)
        
        assert decrypted == plaintext
    
    def test_missing_password(self):
        """Test error when no password is provided."""
        with patch.dict(os.environ, {}, clear=True):
            with pytest.raises(ValueError, match="Encryption password required"):
                ConfigEncryption()
    
    @patch.dict(os.environ, {
        "EDGE_CONFIG_PASSWORD": "test-pass",
        "EDGE_CONFIG_SALT": "MTIzNDU2Nzg5MDEyMzQ1Ng=="  # base64 encoded
    })
    def test_salt_from_environment(self):
        """Test loading salt from environment variable."""
        encryptor = ConfigEncryption()
        
        # Should work without errors
        encrypted = encryptor.encrypt_value("test")
        assert encryptor.decrypt_value(encrypted) == "test"


class TestConfigFileEncryption:
    """Test configuration file encryption functionality."""
    
    def test_encrypt_decrypt_config_file(self):
        """Test encrypting and decrypting configuration files."""
        config = {
            "rtsp_url": "rtsp://admin:pass@camera.local/stream",
            "minio_secret_key": "secret123",
            "device_id": "edge-001",
            "target_fps": 15
        }
        
        with tempfile.TemporaryDirectory() as temp_dir:
            input_file = os.path.join(temp_dir, "config.json")
            encrypted_file = os.path.join(temp_dir, "config.encrypted.json")
            output_file = os.path.join(temp_dir, "config.decrypted.json")
            
            # Write original config
            with open(input_file, 'w') as f:
                json.dump(config, f)
            
            # Generate salt and encrypt
            salt = generate_salt()
            password = "test-password"
            
            encrypt_config_file(input_file, encrypted_file, password, salt)
            
            # Load encrypted config and verify sensitive data is encrypted
            with open(encrypted_file, 'r') as f:
                encrypted_config = json.load(f)
            
            assert encrypted_config["rtsp_url"].startswith("rtsp://encrypted:")
            assert encrypted_config["minio_secret_key"] != "secret123"
            assert encrypted_config["device_id"] == "edge-001"  # Not sensitive
            assert encrypted_config["target_fps"] == 15  # Not sensitive
            
            # Decrypt config file
            decrypt_config_file(encrypted_file, output_file, password, salt)
            
            # Load decrypted config and verify it matches original
            with open(output_file, 'r') as f:
                decrypted_config = json.load(f)
            
            assert decrypted_config == config


class TestUtilityFunctions:
    """Test utility functions."""
    
    def test_generate_salt(self):
        """Test salt generation."""
        salt1 = generate_salt()
        salt2 = generate_salt()
        
        # Should be different
        assert salt1 != salt2
        
        # Should be valid base64
        import base64
        try:
            decoded = base64.b64decode(salt1)
            assert len(decoded) == 16  # Expected salt length
        except Exception:
            pytest.fail("Generated salt is not valid base64")


if __name__ == "__main__":
    # Run basic tests
    test_enc = TestConfigEncryption()
    test_enc.setup_method()
    
    print("Testing basic encryption...")
    test_enc.test_encrypt_decrypt_value()
    print("✓ Basic encryption works")
    
    print("Testing RTSP URL encryption...")
    test_enc.test_rtsp_url_encryption_with_credentials()
    print("✓ RTSP URL encryption works")
    
    print("Testing config dict encryption...")
    test_enc.test_encrypt_config_dict()
    print("✓ Config dict encryption works")
    
    print("\nAll basic tests passed! ✅")
