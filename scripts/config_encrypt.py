#!/usr/bin/env python3
"""
Configuration encryption management CLI.

Utility script for encrypting/decrypting edge configuration files and
environment variables containing sensitive data like RTSP credentials.

Usage:
    python scripts/config_encrypt.py --help
    
Examples:
    # Generate encryption salt
    python scripts/config_encrypt.py generate-salt
    
    # Encrypt RTSP URL
    python scripts/config_encrypt.py encrypt-rtsp "rtsp://admin:pass@camera.local/stream"
    
    # Encrypt configuration file
    python scripts/config_encrypt.py encrypt-file config.json config.encrypted.json
    
    # Decrypt configuration file
    python scripts/config_encrypt.py decrypt-file config.encrypted.json config.json
    
    # Generate environment variables with encrypted values
    python scripts/config_encrypt.py env-encrypt
"""

import argparse
import os
import sys
import json
import getpass
from pathlib import Path

# Add parent directory to Python path for imports
sys.path.append(str(Path(__file__).parent.parent))

try:
    from edge.utils.config_encryption import (
        ConfigEncryption, 
        generate_salt, 
        encrypt_config_file, 
        decrypt_config_file
    )
except ImportError:
    print("Error: Could not import encryption utilities. Make sure you're running from the edge directory.")
    sys.exit(1)


def cmd_generate_salt(args):
    """Generate a new encryption salt."""
    salt = generate_salt()
    print(f"Generated salt: {salt}")
    print(f"\nAdd this to your environment:")
    print(f"export EDGE_CONFIG_SALT='{salt}'")


def cmd_encrypt_rtsp(args):
    """Encrypt an RTSP URL."""
    password = args.password or getpass.getpass("Encryption password: ")
    salt = args.salt
    
    if not salt:
        print("Salt required for RTSP encryption. Use --salt or set EDGE_CONFIG_SALT")
        return 1
    
    try:
        encryptor = ConfigEncryption(password=password, salt=salt.encode() if isinstance(salt, str) else salt)
        encrypted_url = encryptor.encrypt_rtsp_url(args.rtsp_url)
        
        print(f"Original:  {args.rtsp_url}")
        print(f"Encrypted: {encrypted_url}")
        print(f"\nAdd this to your environment:")
        print(f"export RTSP_URL='{encrypted_url}'")
        
    except Exception as e:
        print(f"Error encrypting RTSP URL: {e}")
        return 1
    
    return 0


def cmd_decrypt_rtsp(args):
    """Decrypt an RTSP URL."""
    password = args.password or getpass.getpass("Encryption password: ")
    salt = args.salt
    
    if not salt:
        print("Salt required for RTSP decryption. Use --salt or set EDGE_CONFIG_SALT")
        return 1
    
    try:
        import base64
        salt_bytes = base64.b64decode(salt.encode())
        encryptor = ConfigEncryption(password=password, salt=salt_bytes)
        decrypted_url = encryptor.decrypt_rtsp_url(args.encrypted_rtsp_url)
        
        print(f"Encrypted: {args.encrypted_rtsp_url}")
        print(f"Decrypted: {decrypted_url}")
        
    except Exception as e:
        print(f"Error decrypting RTSP URL: {e}")
        return 1
    
    return 0


def cmd_encrypt_file(args):
    """Encrypt a configuration file."""
    if not os.path.exists(args.input):
        print(f"Input file not found: {args.input}")
        return 1
    
    password = args.password or getpass.getpass("Encryption password: ")
    salt = args.salt
    
    if not salt:
        salt = generate_salt()
        print(f"Generated new salt: {salt}")
        print("Save this salt for decryption!")
    
    try:
        encrypt_config_file(args.input, args.output, password, salt)
        print(f"Configuration encrypted: {args.input} -> {args.output}")
        print(f"Salt: {salt}")
        
    except Exception as e:
        print(f"Error encrypting configuration file: {e}")
        return 1
    
    return 0


def cmd_decrypt_file(args):
    """Decrypt a configuration file."""
    if not os.path.exists(args.input):
        print(f"Input file not found: {args.input}")
        return 1
    
    password = args.password or getpass.getpass("Encryption password: ")
    salt = args.salt
    
    if not salt:
        print("Salt required for decryption. Use --salt argument")
        return 1
    
    try:
        decrypt_config_file(args.input, args.output, password, salt)
        print(f"Configuration decrypted: {args.input} -> {args.output}")
        
    except Exception as e:
        print(f"Error decrypting configuration file: {e}")
        return 1
    
    return 0


def cmd_env_encrypt(args):
    """Generate encrypted environment variables."""
    print("Edge Configuration Encryption Setup")
    print("=" * 40)
    
    password = args.password or getpass.getpass("Encryption password: ")
    salt = args.salt or generate_salt()
    
    print(f"\nUsing salt: {salt}")
    
    try:
        import base64
        salt_bytes = base64.b64decode(salt.encode())
        encryptor = ConfigEncryption(password=password, salt=salt_bytes)
        
        # Environment variables to encrypt
        env_vars = {}
        
        print("\nEnter values to encrypt (press Enter to skip):")
        
        # RTSP URL
        rtsp_url = input("RTSP URL (rtsp://user:pass@host/path): ").strip()
        if rtsp_url:
            env_vars["RTSP_URL"] = encryptor.encrypt_rtsp_url(rtsp_url)
        
        # Backend credentials
        backend_token = input("Backend API token: ").strip()
        if backend_token:
            env_vars["BACKEND_AUTH_TOKEN"] = encryptor.encrypt_value(backend_token)
        
        backend_password = input("Backend password: ").strip()
        if backend_password:
            env_vars["BACKEND_PASSWORD"] = encryptor.encrypt_value(backend_password)
        
        # MinIO credentials
        minio_access = input("MinIO access key: ").strip()
        if minio_access:
            env_vars["MINIO_ACCESS_KEY"] = encryptor.encrypt_value(minio_access)
        
        minio_secret = input("MinIO secret key: ").strip()
        if minio_secret:
            env_vars["MINIO_SECRET_KEY"] = encryptor.encrypt_value(minio_secret)
        
        # Generate output
        print("\n" + "=" * 50)
        print("ENCRYPTED ENVIRONMENT VARIABLES")
        print("=" * 50)
        print()
        print("# Add these to your environment or .env file:")
        print(f"export EDGE_CONFIG_PASSWORD='{password}'")
        print(f"export EDGE_CONFIG_SALT='{salt}'")
        print()
        
        for var_name, encrypted_value in env_vars.items():
            print(f"export {var_name}='{encrypted_value}'")
        
        print("\n# Or for .env file format:")
        print(f"EDGE_CONFIG_PASSWORD={password}")
        print(f"EDGE_CONFIG_SALT={salt}")
        
        for var_name, encrypted_value in env_vars.items():
            print(f"{var_name}={encrypted_value}")
        
        # Save to file if requested
        if args.output:
            with open(args.output, 'w') as f:
                f.write(f"EDGE_CONFIG_PASSWORD={password}\n")
                f.write(f"EDGE_CONFIG_SALT={salt}\n")
                for var_name, encrypted_value in env_vars.items():
                    f.write(f"{var_name}={encrypted_value}\n")
            print(f"\nEnvironment variables saved to: {args.output}")
        
    except Exception as e:
        print(f"Error generating encrypted environment variables: {e}")
        return 1
    
    return 0


def cmd_test_config(args):
    """Test configuration encryption/decryption."""
    password = args.password or "test-password-123"
    salt = args.salt or generate_salt()
    
    print("Testing configuration encryption...")
    
    try:
        import base64
        salt_bytes = base64.b64decode(salt.encode())
        encryptor = ConfigEncryption(password=password, salt=salt_bytes)
        
        # Test basic encryption
        test_value = "secret-test-value"
        encrypted = encryptor.encrypt_value(test_value)
        decrypted = encryptor.decrypt_value(encrypted)
        
        assert decrypted == test_value, "Basic encryption failed"
        print("✓ Basic encryption works")
        
        # Test RTSP URL encryption
        rtsp_url = "rtsp://admin:password123@192.168.1.100/stream"
        encrypted_url = encryptor.encrypt_rtsp_url(rtsp_url)
        decrypted_url = encryptor.decrypt_rtsp_url(encrypted_url)
        
        assert decrypted_url == rtsp_url, "RTSP encryption failed"
        print("✓ RTSP URL encryption works")
        
        # Test config dict encryption
        config = {
            "rtsp_url": rtsp_url,
            "minio_secret_key": "secret123",
            "device_id": "edge-001"
        }
        
        encrypted_config = encryptor.encrypt_config_dict(config)
        decrypted_config = encryptor.decrypt_config_dict(encrypted_config)
        
        assert decrypted_config == config, "Config dict encryption failed"
        print("✓ Configuration dictionary encryption works")
        
        print("\nAll tests passed! ✅")
        
    except Exception as e:
        print(f"Test failed: {e}")
        return 1
    
    return 0


def main():
    """Main CLI entry point."""
    parser = argparse.ArgumentParser(
        description="Edge configuration encryption management",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=__doc__
    )
    
    # Global arguments
    parser.add_argument("--password", help="Encryption password (or set EDGE_CONFIG_PASSWORD)")
    parser.add_argument("--salt", help="Base64-encoded salt (or set EDGE_CONFIG_SALT)")
    
    subparsers = parser.add_subparsers(dest="command", help="Commands")
    
    # Generate salt command
    salt_parser = subparsers.add_parser("generate-salt", help="Generate encryption salt")
    
    # Encrypt RTSP URL command
    rtsp_encrypt_parser = subparsers.add_parser("encrypt-rtsp", help="Encrypt RTSP URL")
    rtsp_encrypt_parser.add_argument("rtsp_url", help="RTSP URL to encrypt")
    
    # Decrypt RTSP URL command
    rtsp_decrypt_parser = subparsers.add_parser("decrypt-rtsp", help="Decrypt RTSP URL")
    rtsp_decrypt_parser.add_argument("encrypted_rtsp_url", help="Encrypted RTSP URL to decrypt")
    
    # Encrypt file command
    file_encrypt_parser = subparsers.add_parser("encrypt-file", help="Encrypt configuration file")
    file_encrypt_parser.add_argument("input", help="Input configuration file")
    file_encrypt_parser.add_argument("output", help="Output encrypted file")
    
    # Decrypt file command
    file_decrypt_parser = subparsers.add_parser("decrypt-file", help="Decrypt configuration file")
    file_decrypt_parser.add_argument("input", help="Input encrypted file")
    file_decrypt_parser.add_argument("output", help="Output decrypted file")
    
    # Environment encryption command
    env_encrypt_parser = subparsers.add_parser("env-encrypt", help="Generate encrypted environment variables")
    env_encrypt_parser.add_argument("--output", help="Output file for environment variables")
    
    # Test command
    test_parser = subparsers.add_parser("test", help="Test encryption functionality")
    
    args = parser.parse_args()
    
    if not args.command:
        parser.print_help()
        return 1
    
    # Get salt from environment if not provided
    if not args.salt:
        args.salt = os.getenv("EDGE_CONFIG_SALT")
    
    # Command dispatch
    commands = {
        "generate-salt": cmd_generate_salt,
        "encrypt-rtsp": cmd_encrypt_rtsp,
        "decrypt-rtsp": cmd_decrypt_rtsp,
        "encrypt-file": cmd_encrypt_file,
        "decrypt-file": cmd_decrypt_file,
        "env-encrypt": cmd_env_encrypt,
        "test": cmd_test_config
    }
    
    if args.command in commands:
        return commands[args.command](args)
    else:
        print(f"Unknown command: {args.command}")
        return 1


if __name__ == "__main__":
    sys.exit(main())
