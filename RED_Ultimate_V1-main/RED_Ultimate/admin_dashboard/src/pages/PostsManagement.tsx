import { useCallback, useEffect, useState } from 'react';
import {
  Avatar, Button, Card, Col, Input, message, Popconfirm, Row, Space,
  Statistic, Switch, Table, Tag, Tooltip, Typography
} from 'antd';
import {
  DeleteOutlined, FileTextOutlined, LikeOutlined, MessageOutlined,
  PictureOutlined, ReloadOutlined, RetweetOutlined, SearchOutlined,
  UndoOutlined
} from '@ant-design/icons';
import {
  deleteAdminPost, getAdminPosts, getPostsOverview, restoreAdminPost,
  type AdminPost
} from '../api';

const { Title, Text, Paragraph } = Typography;

const VISIBILITY_LABELS: Record<string, { label: string; color: string }> = {
  PUBLIC: { label: 'عام', color: 'gold' },
  LOCAL_YEMEN: { label: 'محلي (اليمن)', color: 'orange' },
};

/** إدارة المنشورات والتغريدات — بيانات حقيقية من /api/admin/social/posts */
export default function PostsManagement() {
  const [overview, setOverview] = useState<{ totalPosts: number; createdToday: number; deletedPosts: number; polls: number } | null>(null);
  const [posts, setPosts] = useState<AdminPost[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(20);
  const [search, setSearch] = useState('');
  const [includeDeleted, setIncludeDeleted] = useState(false);
  const [loading, setLoading] = useState(false);

  const loadOverview = useCallback(async () => {
    try { setOverview(await getPostsOverview()); } catch { /* الإحصائيات ثانوية */ }
  }, []);

  const loadPosts = useCallback(async (p = page, s = pageSize, q = search, deleted = includeDeleted) => {
    setLoading(true);
    try {
      const data = await getAdminPosts({ q: q || undefined, includeDeleted: deleted, page: p, size: s });
      setPosts(data.content);
      setTotal(data.totalElements);
    } catch (e: any) {
      message.error(e?.message || 'تعذّر تحميل المنشورات');
    } finally {
      setLoading(false);
    }
  }, [page, pageSize, search, includeDeleted]);

  useEffect(() => { loadOverview(); }, [loadOverview]);
  useEffect(() => { loadPosts(); }, [loadPosts]);

  const onDelete = async (post: AdminPost) => {
    try {
      await deleteAdminPost(post.id);
      message.success('حُذف المنشور');
      loadPosts();
      loadOverview();
    } catch (e: any) {
      message.error(e?.message || 'تعذّر حذف المنشور');
    }
  };

  const onRestore = async (post: AdminPost) => {
    try {
      await restoreAdminPost(post.id);
      message.success('استُعيد المنشور');
      loadPosts();
      loadOverview();
    } catch (e: any) {
      message.error(e?.message || 'تعذّرت استعادة المنشور');
    }
  };

  const reactionsTotal = (p: AdminPost) =>
    Object.values(p.reactionCounts || {}).reduce((sum, n) => sum + n, 0);

  const columns = [
    {
      title: 'الكاتب', key: 'author', width: 170,
      render: (_: unknown, p: AdminPost) => (
        <Space>
          <Avatar size="small">{p.authorDisplayName?.trim()?.charAt(0) || '؟'}</Avatar>
          <div>
            <div>{p.authorDisplayName}</div>
            <Text type="secondary" style={{ fontSize: 11 }}>@{p.authorUsername}</Text>
          </div>
        </Space>
      ),
    },
    {
      title: 'النص', dataIndex: 'text', key: 'text',
      render: (v: string, p: AdminPost) => (
        <div>
          <Paragraph ellipsis={{ rows: 2 }} style={{ margin: 0 }}>{v || <Text type="secondary" italic>بلا نص</Text>}</Paragraph>
          <Space size={4} wrap>
            {p.kind === 'POLL' && <Tag color="purple">استطلاع</Tag>}
            {p.mediaCount > 0 && <Tag icon={<PictureOutlined />} color="blue">{p.mediaCount} وسائط</Tag>}
            {p.hashtags.slice(0, 3).map((h) => <Tag key={h}>#{h}</Tag>)}
          </Space>
        </div>
      ),
    },
    {
      title: 'الظهور', dataIndex: 'visibility', key: 'visibility', width: 110,
      render: (v: string) => {
        const vis = VISIBILITY_LABELS[v] || { label: v, color: 'default' };
        return <Tag color={vis.color}>{vis.label}</Tag>;
      },
    },
    {
      title: 'التفاعل', key: 'engagement', width: 150,
      render: (_: unknown, p: AdminPost) => (
        <Space size={10}>
          <Tooltip title="تفاعلات"><span><LikeOutlined /> {reactionsTotal(p)}</span></Tooltip>
          <Tooltip title="ردود"><span><MessageOutlined /> {p.replyCount}</span></Tooltip>
          <Tooltip title="إعادات نشر"><span><RetweetOutlined /> {p.repostCount}</span></Tooltip>
        </Space>
      ),
    },
    {
      title: 'الحالة', key: 'status', width: 90,
      render: (_: unknown, p: AdminPost) =>
        p.deleted ? <Tag color="red">محذوف</Tag> : <Tag color="gold">منشور</Tag>,
    },
    {
      title: 'نُشر', dataIndex: 'createdAt', key: 'createdAt', width: 120,
      render: (v: string) => new Date(v).toLocaleDateString('ar-YE'),
    },
    {
      title: 'إجراءات', key: 'actions', width: 110,
      render: (_: unknown, p: AdminPost) =>
        p.deleted ? (
          <Popconfirm
            title="استعادة المنشور؟" okText="استعادة" cancelText="إلغاء"
            onConfirm={() => onRestore(p)}
          >
            <Button size="small" icon={<UndoOutlined />}>استعادة</Button>
          </Popconfirm>
        ) : (
          <Popconfirm
            title="حذف المنشور؟"
            description="حذف ناعم — يبقى قابلًا للاستعادة."
            okText="حذف" cancelText="إلغاء" okButtonProps={{ danger: true }}
            onConfirm={() => onDelete(p)}
          >
            <Button size="small" danger icon={<DeleteOutlined />} />
          </Popconfirm>
        ),
    },
  ];

  return (
    <div>
      <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
        <Col xs={12} md={6}>
          <Card><Statistic title="إجمالي المنشورات" value={overview?.totalPosts ?? '—'} prefix={<FileTextOutlined />} /></Card>
        </Col>
        <Col xs={12} md={6}>
          <Card><Statistic title="نُشر اليوم" value={overview?.createdToday ?? '—'} valueStyle={{ color: '#B78A2E' }} /></Card>
        </Col>
        <Col xs={12} md={6}>
          <Card><Statistic title="المحذوفة" value={overview?.deletedPosts ?? '—'} valueStyle={{ color: '#E0A83C' }} /></Card>
        </Col>
        <Col xs={12} md={6}>
          <Card><Statistic title="الاستطلاعات" value={overview?.polls ?? '—'} /></Card>
        </Col>
      </Row>

      <Card
        title={<Title level={4} style={{ margin: 0 }}>إدارة المنشورات والتغريدات</Title>}
        extra={
          <Space>
            <span>
              <Text type="secondary" style={{ marginInlineEnd: 6 }}>إظهار المحذوف</Text>
              <Switch
                checked={includeDeleted}
                onChange={(v) => { setIncludeDeleted(v); setPage(0); loadPosts(0, pageSize, search, v); }}
              />
            </span>
            <Input
              allowClear
              prefix={<SearchOutlined />}
              placeholder="بحث بالنص أو الكاتب…"
              style={{ width: 240 }}
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              onPressEnter={() => { setPage(0); loadPosts(0, pageSize, search); }}
            />
            <Button icon={<ReloadOutlined />} onClick={() => { loadPosts(); loadOverview(); }}>تحديث</Button>
          </Space>
        }
      >
        <Table
          rowKey="id"
          loading={loading}
          columns={columns}
          dataSource={posts}
          pagination={{
            current: page + 1,
            pageSize,
            total,
            showSizeChanger: true,
            showTotal: (t) => `${t} منشور`,
          }}
          onChange={(p) => {
            setPage((p.current ?? 1) - 1);
            setPageSize(p.pageSize ?? 20);
            loadPosts((p.current ?? 1) - 1, p.pageSize ?? 20);
          }}
        />
      </Card>
    </div>
  );
}
