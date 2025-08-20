'use client';

import { Card, Row, Col, Statistic, Typography, Space, Button } from 'antd';
import { 
  UserOutlined, 
  ClockCircleOutlined, 
  CheckCircleOutlined,
  ExclamationCircleOutlined 
} from '@ant-design/icons';
import AppLayout from '@/components/layout/AppLayout';
import ProtectedRoute from '@/components/layout/ProtectedRoute';

const { Title } = Typography;

/**
 * Dashboard page with key metrics and overview
 */
export default function DashboardPage() {
  return (
    <ProtectedRoute>
      <AppLayout>
        <Space direction="vertical" size="large" style={{ width: '100%' }}>
          <Title level={2}>Dashboard</Title>
          
          <Row gutter={[16, 16]}>
            <Col xs={24} sm={12} lg={6}>
              <Card>
                <Statistic
                  title="Total Employees"
                  value={142}
                  prefix={<UserOutlined />}
                  valueStyle={{ color: '#3f8600' }}
                />
              </Card>
            </Col>
            
            <Col xs={24} sm={12} lg={6}>
              <Card>
                <Statistic
                  title="Present Today"
                  value={98}
                  prefix={<CheckCircleOutlined />}
                  valueStyle={{ color: '#3f8600' }}
                  suffix="/ 142"
                />
              </Card>
            </Col>
            
            <Col xs={24} sm={12} lg={6}>
              <Card>
                <Statistic
                  title="Late Arrivals"
                  value={12}
                  prefix={<ExclamationCircleOutlined />}
                  valueStyle={{ color: '#cf1322' }}
                />
              </Card>
            </Col>
            
            <Col xs={24} sm={12} lg={6}>
              <Card>
                <Statistic
                  title="Active Devices"
                  value={8}
                  prefix={<ClockCircleOutlined />}
                  valueStyle={{ color: '#1890ff' }}
                />
              </Card>
            </Col>
          </Row>

          <Row gutter={[16, 16]}>
            <Col xs={24} lg={16}>
              <Card 
                title="Recent Attendance" 
                extra={<Button type="link">View All</Button>}
                style={{ height: 400 }}
              >
                <div style={{ 
                  display: 'flex', 
                  alignItems: 'center', 
                  justifyContent: 'center', 
                  height: '100%',
                  color: '#8c8c8c' 
                }}>
                  Attendance records will be displayed here
                </div>
              </Card>
            </Col>
            
            <Col xs={24} lg={8}>
              <Card 
                title="System Status" 
                style={{ height: 400 }}
              >
                <Space direction="vertical" size="middle" style={{ width: '100%' }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                    <span>Database</span>
                    <CheckCircleOutlined style={{ color: '#52c41a' }} />
                  </div>
                  <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                    <span>MinIO Storage</span>
                    <CheckCircleOutlined style={{ color: '#52c41a' }} />
                  </div>
                  <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                    <span>Recognition Service</span>
                    <CheckCircleOutlined style={{ color: '#52c41a' }} />
                  </div>
                  <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                    <span>Active Cameras</span>
                    <span style={{ color: '#1890ff' }}>8/10</span>
                  </div>
                </Space>
              </Card>
            </Col>
          </Row>
        </Space>
      </AppLayout>
    </ProtectedRoute>
  );
}
