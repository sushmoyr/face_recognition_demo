# Face Recognition Attendance System - Frontend

Modern web application built with Next.js 15 for managing face recognition-based attendance tracking.

## 🚀 Tech Stack

- **Framework**: Next.js 15 with App Router
- **Language**: TypeScript
- **UI Library**: Ant Design (antd)
- **State Management**: Zustand with persistence
- **HTTP Client**: Axios with interceptors
- **Data Fetching**: TanStack Query (React Query)
- **Form Handling**: React Hook Form with Zod validation
- **Styling**: Tailwind CSS + Ant Design
- **Authentication**: JWT-based with automatic token refresh

## 📋 Features

- **Authentication System**
  - JWT-based login/logout
  - Automatic token refresh
  - Role-based access control (Admin, Manager, User)
  - Protected routes with middleware

- **Dashboard**
  - Key attendance metrics
  - System status monitoring
  - Recent activity overview

- **Employee Management**
  - Employee CRUD operations
  - Face template enrollment
  - Employee photo management

- **Device Management**
  - Camera/device registration
  - Device status monitoring
  - Configuration management

- **Attendance Tracking**
  - Real-time attendance records
  - In/Out time tracking
  - Late arrival notifications

- **Reporting**
  - Daily/weekly/monthly reports
  - Attendance analytics
  - Export capabilities

## 🏗️ Project Structure

```
src/
├── app/                    # Next.js App Router pages
│   ├── dashboard/          # Dashboard page
│   ├── login/             # Authentication page
│   ├── layout.tsx         # Root layout with providers
│   └── page.tsx           # Home page (redirects)
├── components/
│   ├── layout/            # Layout components
│   │   ├── AppLayout.tsx  # Main app layout with sidebar
│   │   └── ProtectedRoute.tsx # Route protection wrapper
│   └── ui/                # Reusable UI components
├── hooks/
│   └── useAuth.ts         # Authentication hook
├── lib/
│   ├── api/               # API client and services
│   │   ├── client.ts      # Axios configuration
│   │   └── auth.ts        # Authentication API
│   └── auth/
│       └── types.ts       # Auth-related TypeScript types
├── stores/
│   └── auth.ts           # Zustand authentication store
└── middleware.ts         # Next.js middleware for route protection
```

## 🔧 Environment Variables

Create a `.env.local` file in the frontend directory:

```env
# Backend API
NEXT_PUBLIC_API_URL=http://localhost:8080

# MinIO Configuration
NEXT_PUBLIC_MINIO_ENDPOINT=localhost:9000
NEXT_PUBLIC_MINIO_BUCKET=face-recognition
NEXT_PUBLIC_MINIO_USE_SSL=false
```

## 📦 Installation & Setup

1. **Install dependencies:**
   ```bash
   npm install
   ```

2. **Start development server:**
   ```bash
   npm run dev
   ```

3. **Access the application:**
   - Open http://localhost:3000
   - Login with demo credentials:
     - Admin: `admin` / `admin123`
     - Manager: `manager` / `manager123`

## 🔗 API Integration

The frontend communicates with the Spring Boot backend through:

- **Authentication**: `/api/auth/*` endpoints
- **Employees**: `/api/employees/*` endpoints  
- **Devices**: `/api/devices/*` endpoints
- **Attendance**: `/api/attendance/*` endpoints
- **Reports**: `/api/reports/*` endpoints

## 🛡️ Security Features

- **JWT Authentication**: Secure token-based authentication
- **Automatic Token Refresh**: Seamless session management
- **Route Protection**: Middleware-based access control
- **Role-Based Access**: Different permissions for Admin/Manager/User
- **Secure Cookie Storage**: HttpOnly cookies for token storage
- **CSRF Protection**: Built-in Next.js CSRF protection

## 🚀 Build & Deployment

1. **Build for production:**
   ```bash
   npm run build
   ```

2. **Start production server:**
   ```bash
   npm start
   ```

3. **Docker deployment:**
   ```bash
   docker build -t face-recognition-frontend .
   docker run -p 3000:3000 face-recognition-frontend
   ```

## 🧪 Development

- **Type checking**: `npm run type-check`
- **Linting**: `npm run lint`
- **Code formatting**: `npm run format`

## 📝 Frontend Milestones

### Milestone 1: Authentication & Layout ✅
- [x] Next.js 15 project setup
- [x] Authentication system with JWT
- [x] Protected routes with middleware
- [x] Main layout with sidebar navigation
- [x] Login page with form validation

### Milestone 2: Employee Management (Next)
- [ ] Employee list with search/filter
- [ ] Employee creation/editing forms
- [ ] Face photo upload and management
- [ ] Employee enrollment workflow

### Milestone 3: Dashboard & Analytics
- [ ] Real-time attendance dashboard
- [ ] Key metrics and statistics
- [ ] Charts and visualizations
- [ ] System status monitoring

### Milestone 4: Device Management
- [ ] Device registration interface
- [ ] Live camera feed preview
- [ ] Device configuration panel
- [ ] Status monitoring dashboard

### Milestone 5: Attendance & Reports
- [ ] Attendance records view
- [ ] Advanced filtering and search
- [ ] Report generation interface
- [ ] Data export functionality

---

**Note**: This frontend application requires the Spring Boot backend to be running on `http://localhost:8080` for full functionality.

## Deploy on Vercel

The easiest way to deploy your Next.js app is to use the [Vercel Platform](https://vercel.com/new?utm_medium=default-template&filter=next.js&utm_source=create-next-app&utm_campaign=create-next-app-readme) from the creators of Next.js.

Check out our [Next.js deployment documentation](https://nextjs.org/docs/app/building-your-application/deploying) for more details.
