import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  // Enable experimental features for Next.js 15
  experimental: {
    optimizePackageImports: ['antd', 'lucide-react'],
  },
  
  // Image optimization for face recognition images
  images: {
    remotePatterns: [
      {
        protocol: 'http',
        hostname: 'localhost',
        port: '9000', // MinIO port
      },
      {
        protocol: 'http',
        hostname: 'minio',
        port: '9000',
      }
    ],
  },
  
  // Transpile packages for compatibility
  transpilePackages: ['antd'],
  
  // Environment variables
  env: {
    NEXT_PUBLIC_API_URL: process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080',
    NEXT_PUBLIC_WS_URL: process.env.NEXT_PUBLIC_WS_URL || 'ws://localhost:8081',
  },
};

export default nextConfig;
