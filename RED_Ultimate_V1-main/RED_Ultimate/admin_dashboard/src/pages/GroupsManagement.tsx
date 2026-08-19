import { useCallback, useEffect, useState } from 'react';
import {
  Avatar, Button, Card, Col, Drawer, Input, message, Popconfirm, Row,
  Space, Statistic, Table, Tag, Typography
} from 'antd';
import {
  CrownOutlined, DeleteOutlined, ReloadOutlined, SearchOutlined,
  TeamOutlined, UserOutlined
} from '@ant-design/icons';
import {
  deleteAdminGroup, getAdminGroupDetails, getAdminGroups, getGroupsOverview,
  removeGroupMember, type AdminGroup, type AdminGroupDetails
} from '../api';

const { Title, Text, Paragraph } = Typography;

const ROLE_LABELS: Record<string, { label: string; color: string }> = {
  OWNER: { label: 'مالك', color: 'gold' },
  ADMIN: { label: 'مشرف', color: 'blue' },
  MEMBER: { label: 'عضو', color: 'default' },
};

/** إدارة المجموعات — بيانات حقيقية من /api/admin/social/groups */
export default function GroupsManagement() {
  const [overview, setOverview] = useState<{ totalGroups: number; totalMembers: number; avgMembersPerGroup: number; createdToday: number } | null>(null);
  const [groups, setGroups] = useState<AdminGroup[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(20);
  const [search, setSearch] = useState('');
  const [loading, setLoading] = useState(false);
  const [details, setDetails] = useState<AdminGroupDetails | null>(null);
  const [detailsLoading, setDetailsLoading] = useState(false);
  const [drawerOpen, setDrawerOpen] = useState(false);

  const loadOverview = useCallback(async () => {
    try { setOverview(await getGroupsOverview()); } catch { /* الإحصائيات ثانوية */ }
  }, []);

  const loadGroups = useCallback(async (p = page, s = pageSize, q = search) => {
    setLoading(true);
    try {
      const data = await getAdminGroups({ q: q || undefined, page: p, size: s });
      setGroups(data.content);
      setTotal(data.totalElements);
    } catch (e: any) {
      message.error(e?.message || 'تعذّر تحميل المجموعات');
    } finally {
      setLoading(false);
    }
  }, [page, pageSize, search]);

  useEffect(() => { loadOverview(); }, [loadOverview]);
  useEffect(() => { loadGroups(); }, [loadGroups]);

  const openDetails = async (group: AdminGroup) => {
    setDrawerOpen(true);
    setDetailsLoading(true);
    try {
      setDetails(await getAdminGroupDetails(group.id));
    } catch (e: any) {
      message.error(e?.message || 'تعذّر تحميل تفاصيل المجموعة');
      setDrawerOpen(false);
    } finally {
      setDetailsLoading(false);
    }
  };

  const onDeleteGroup = async (group: AdminGroup) => {
    try {
      await deleteAdminGroup(group.id);
      message.success(`حُذفت المجموعة «${group.name}»`);
      setDrawerOpen(false);
      loadGroups();
      loadOverview();
    } catch (e: any) {
      message.error(e?.message || 'تعذّر حذف المجموعة');
    }
  };

  const onRemoveMember = async (userId: string, username: string) => {
    if (!details) return;
    try {
      await removeGroupMember(details.id, userId);
      message.success(`أُزيل ${username} من المجموعة`);
      setDetails(await getAdminGroupDetails(details.id));
      loadGroups();
      loadOverview();
    } catch (e: any) {
      message.error(e?.message || 'تعذّرت إزالة العضو');
    }
  };

  const columns = [
    {
      title: 'المجموعة', key: 'name',
      render: (_: unknown, g: AdminGroup) => (
        <Space>
          <Avatar src={g.avatarUrl || undefined} icon={<TeamOutlined />} />
          <div>
            <div><Text strong>{g.name}</Text></div>
            {g.description && <Text type="secondary" style={{ fontSize: 12 }}>{g.description}</Text>}
          </div>
        </Space>
      ),
    },
    { title: 'المالك', dataIndex: 'ownerRedId', key: 'ownerRedId', render: (v: string) => <Text code>{v}</Text> },
    {
      title: 'الأعضاء', dataIndex: 'memberCount', key: 'memberCount', width: 100,
      sorter: (a: AdminGroup, b: AdminGroup) => a.memberCount - b.memberCount,
      render: (v: number) => <Tag icon={<UserOutlined />} color="cyan">{v}</Tag>,
    },
    {
      title: 'أُنشئت', dataIndex: 'createdAt', key: 'createdAt', width: 130,
      render: (v: string) => new Date(v).toLocaleDateString('ar-YE'),
    },
    {
      title: 'إجراءات', key: 'actions', width: 190,
      render: (_: unknown, g: AdminGroup) => (
        <Space>
          <Button size="small" onClick={() => openDetails(g)}>التفاصيل</Button>
          <Popconfirm
            title={`حذف «${g.name}» نهائيًا؟`}
            description="تُحذف المجموعة وكل عضوياتها."
            okText="حذف" cancelText="إلغاء" okButtonProps={{ danger: true }}
            onConfirm={() => onDeleteGroup(g)}
          >
            <Button size="small" danger icon={<DeleteOutlined />} />
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <div>
      <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
        <Col xs={12} md={6}>
          <Card><Statistic title="إجمالي المجموعات" value={overview?.totalGroups ?? '—'} prefix={<TeamOutlined />} /></Card>
        </Col>
        <Col xs={12} md={6}>
          <Card><Statistic title="إجمالي العضويات" value={overview?.totalMembers ?? '—'} prefix={<UserOutlined />} /></Card>
        </Col>
        <Col xs={12} md={6}>
          <Card><Statistic title="متوسط الأعضاء" value={overview ? overview.avgMembersPerGroup.toFixed(1) : '—'} /></Card>
        </Col>
        <Col xs={12} md={6}>
          <Card><Statistic title="أُنشئت اليوم" value={overview?.createdToday ?? '—'} valueStyle={{ color: '#00C896' }} /></Card>
        </Col>
      </Row>

      <Card
        title={<Title level={4} style={{ margin: 0 }}>إدارة المجموعات</Title>}
        extra={
          <Space>
            <Input
              allowClear
              prefix={<SearchOutlined />}
              placeholder="بحث باسم المجموعة…"
              style={{ width: 240 }}
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              onPressEnter={() => { setPage(0); loadGroups(0, pageSize, search); }}
            />
            <Button icon={<ReloadOutlined />} onClick={() => { loadGroups(); loadOverview(); }}>تحديث</Button>
          </Space>
        }
      >
        <Table
          rowKey="id"
          loading={loading}
          columns={columns}
          dataSource={groups}
          pagination={{
            current: page + 1,
            pageSize,
            total,
            showSizeChanger: true,
            showTotal: (t) => `${t} مجموعة`,
          }}
          onChange={(p) => {
            setPage((p.current ?? 1) - 1);
            setPageSize(p.pageSize ?? 20);
            loadGroups((p.current ?? 1) - 1, p.pageSize ?? 20);
          }}
        />
      </Card>

      <Drawer
        title={details ? (
          <Space>
            <Avatar src={details.avatarUrl || undefined} icon={<TeamOutlined />} />
            <span>{details.name}</span>
          </Space>
        ) : 'تفاصيل المجموعة'}
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        width={520}
        loading={detailsLoading}
      >
        {details && (
          <>
            {details.description && <Paragraph type="secondary">{details.description}</Paragraph>}
            <Paragraph>
              <Text type="secondary">المالك: </Text><Text code>{details.ownerRedId}</Text>
              <br />
              <Text type="secondary">أُنشئت: </Text>{new Date(details.createdAt).toLocaleString('ar-YE')}
            </Paragraph>
            <Title level={5}>الأعضاء ({details.members.length})</Title>
            <Table
              rowKey="userId"
              size="small"
              dataSource={details.members}
              pagination={details.members.length > 10 ? { pageSize: 10 } : false}
              columns={[
                {
                  title: 'العضو', key: 'username',
                  render: (_: unknown, m) => (
                    <Space>
                      <Avatar size="small" icon={<UserOutlined />} />
                      <div>
                        <div>{m.username}</div>
                        <Text type="secondary" style={{ fontSize: 11 }} code>{m.redId}</Text>
                      </div>
                    </Space>
                  ),
                },
                {
                  title: 'الدور', dataIndex: 'role', key: 'role', width: 90,
                  render: (v: string) => {
                    const r = ROLE_LABELS[v] || { label: v, color: 'default' };
                    return <Tag icon={v === 'OWNER' ? <CrownOutlined /> : undefined} color={r.color}>{r.label}</Tag>;
                  },
                },
                {
                  title: 'انضم', dataIndex: 'joinedAt', key: 'joinedAt', width: 110,
                  render: (v: string) => new Date(v).toLocaleDateString('ar-YE'),
                },
                {
                  title: '', key: 'actions', width: 60,
                  render: (_: unknown, m) => m.role !== 'OWNER' && (
                    <Popconfirm
                      title={`إزالة ${m.username}؟`}
                      okText="إزالة" cancelText="إلغاء" okButtonProps={{ danger: true }}
                      onConfirm={() => onRemoveMember(m.userId, m.username)}
                    >
                      <Button size="small" danger type="text" icon={<DeleteOutlined />} />
                    </Popconfirm>
                  ),
                },
              ]}
            />
          </>
        )}
      </Drawer>
    </div>
  );
}
