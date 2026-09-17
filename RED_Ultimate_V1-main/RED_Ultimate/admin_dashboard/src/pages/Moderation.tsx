'use client';

import { useState } from 'react';
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Tabs, TabsList, TabsTrigger, TabsContent } from '@/components/ui/tabs';
import { Input } from '@/components/ui/input';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { useTranslation } from 'react-i18next';
import { Shield, Flag, Trash2, CheckCircle, XCircle, AlertTriangle, Search, Filter, Eye, MoreVertical } from 'lucide-react';
import { cn } from '@/utils/cn';
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuLabel, DropdownMenuSeparator, DropdownMenuTrigger } from '@/components/ui/dropdown-menu';

interface ModerationItem {
  id: string;
  type: 'reported' | 'spam' | 'illegal';
  content: string;
  author: string;
  channel: string;
  reason: string;
  priority: 'P0' | 'P1' | 'P2';
  status: 'pending' | 'approved' | 'rejected' | 'deleted';
  createdAt: string;
  reportedBy: string;
}

const mockData: ModerationItem[] = [
  { id: '1', type: 'reported', content: 'Inappropriate content...', author: 'user123', channel: '#general', reason: 'Harassment', priority: 'P0', status: 'pending', createdAt: '2024-01-15T10:30:00Z', reportedBy: 'user456' },
  { id: '2', type: 'spam', content: 'Buy now! Click here!', author: 'spammer', channel: '#random', reason: 'Spam detection', priority: 'P1', status: 'pending', createdAt: '2024-01-15T10:25:00Z', reportedBy: 'system' },
  { id: '3', type: 'illegal', content: 'Illegal content...', author: 'baduser', channel: '#private', reason: 'Illegal activity', priority: 'P0', status: 'pending', createdAt: '2024-01-15T10:20:00Z', reportedBy: 'user789' },
];

export function ModerationPage() {
  const { t } = useTranslation();
  const [activeTab, setActiveTab] = useState<'queue' | 'rules' | 'analytics' | 'broadcast'>('queue');
  const [search, setSearch] = useState('');
  const [filterType, setFilterType] = useState<'all' | 'reported' | 'spam' | 'illegal'>('all');
  const [filterPriority, setFilterPriority] = useState<'all' | 'P0' | 'P1' | 'P2'>('all');

  const filteredData = mockData.filter(item => {
    if (filterType !== 'all' && item.type !== filterType) return false;
    if (filterPriority !== 'all' && item.priority !== filterPriority) return false;
    if (search && !item.content.toLowerCase().includes(search.toLowerCase()) && !item.author.toLowerCase().includes(search.toLowerCase())) return false;
    return true;
  });

  const handleAction = (action: string, item: ModerationItem) => {
    console.log(`${action} item ${item.id}`);
  };

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="text-3xl font-bold tracking-tight">{t('moderation.title')}</h1>
          <p className="text-muted-foreground">{t('moderation.subtitle')}</p>
        </div>
        <Button><Shield className="h-4 w-4 mr-2" /> Auto-Moderate</Button>
      </div>

      <Tabs value={activeTab} onValueChange={setActiveTab} className="space-y-4">
        <TabsList>
          <TabsTrigger value="queue"><Flag className="h-4 w-4 mr-2" /> Queue</TabsTrigger>
          <TabsTrigger value="rules"><Settings className="h-4 w-4 mr-2" /> Rules</TabsTrigger>
          <TabsTrigger value="analytics"><BarChart3 className="h-4 w-4 mr-2" /> Analytics</TabsTrigger>
          <TabsTrigger value="broadcast"><Send className="h-4 w-4 mr-2" /> Broadcast</TabsTrigger>
        </TabsList>

        <TabsContent value="queue">
          <Card>
            <CardHeader>
              <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
                <div>
                  <CardTitle>Moderation Queue</CardTitle>
                  <CardDescription>Review and take action on reported content</CardDescription>
                </div>
                <div className="flex items-center gap-2">
                  <div className="relative">
                    <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
                    <Input placeholder="Search content..." value={search} onChange={e => setSearch(e.target.value)} className="pl-10 w-[250px]" />
                  </div>
                  <Select value={filterType} onValueChange={setFilterType}>
                    <SelectTrigger className="w-[140px]"><SelectValue placeholder="Type" /></SelectTrigger>
                    <SelectContent>
                      <SelectItem value="all">All Types</SelectItem>
                      <SelectItem value="reported">Reported</SelectItem>
                      <SelectItem value="spam">Spam</SelectItem>
                      <SelectItem value="illegal">Illegal</SelectItem>
                    </SelectContent>
                  </Select>
                  <Select value={filterPriority} onValueChange={setFilterPriority}>
                    <SelectTrigger className="w-[120px]"><SelectValue placeholder="Priority" /></SelectTrigger>
                    <SelectContent>
                      <SelectItem value="all">All</SelectItem>
                      <SelectItem value="P0">P0 Critical</SelectItem>
                      <SelectItem value="P1">P1 High</SelectItem>
                      <SelectItem value="P2">P2 Medium</SelectItem>
                    </SelectContent>
                  </Select>
                </div>
              </div>
            </CardHeader>
            <CardContent>
              <div className="space-y-3">
                {filteredData.map((item) => (
                  <div key={item.id} className="border rounded-lg p-4">
                    <div className="flex items-start justify-between gap-4">
                      <div className="flex-1 min-w-0">
                        <div className="flex items-center gap-2 mb-2">
                          <Badge variant={item.priority === 'P0' ? 'destructive' : item.priority === 'P1' ? 'warning' : 'info'}>{item.priority}</Badge>
                          <Badge variant="outline">{item.type}</Badge>
                          <Badge variant={item.status === 'pending' ? 'warning' : item.status === 'approved' ? 'success' : 'destructive'}>{item.status}</Badge>
                        </div>
                        <p className="text-muted-foreground mb-2">{item.content}</p>
                        <div className="flex items-center gap-4 text-sm text-muted-foreground">
                          <span>Author: {item.author}</span>
                          <span>Channel: {item.channel}</span>
                          <span>Reported by: {item.reportedBy}</span>
                          <span>{new Date(item.createdAt).toLocaleString()}</span>
                        </div>
                        <p className="text-sm mt-1">Reason: {item.reason}</p>
                      </div>
                      <DropdownMenu>
                        <DropdownMenuTrigger asChild>
                          <Button variant="ghost" size="icon"><MoreVertical className="h-4 w-4" /></Button>
                        </DropdownMenuTrigger>
                        <DropdownMenuContent align="end">
                          <DropdownMenuLabel>Actions for {item.author}</DropdownMenuLabel>
                          <DropdownMenuSeparator />
                          <DropdownMenuItem onClick={() => handleAction('approve', item)} className="text-green-600">
                            <CheckCircle className="h-4 w-4 mr-2" /> Approve
                          </DropdownMenuItem>
                          <DropdownMenuItem onClick={() => handleAction('reject', item)} className="text-red-600">
                            <XCircle className="h-4 w-4 mr-2" /> Reject
                          </DropdownMenuItem>
                          <DropdownMenuItem onClick={() => handleAction('delete', item)} className="text-red-600">
                            <Trash2 className="h-4 w-4 mr-2" /> Delete
                          </DropdownMenuItem>
                          <DropdownMenuItem onClick={() => handleAction('warn', item)} className="text-orange-600">
                            <AlertTriangle className="h-4 w-4 mr-2" /> Warn User
                          </DropdownMenuItem>
                          <DropdownMenuSeparator />
                          <DropdownMenuItem onClick={() => handleAction('view', item)}>
                            <Eye className="h-4 w-4 mr-2" /> View Full Content
                          </DropdownMenuItem>
                        </DropdownMenuContent>
                      </DropdownMenu>
                    </div>
                  </div>
                ))}
              </div>
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="rules">
          <Card>
            <CardHeader>
              <CardTitle>Automated Moderation Rules</CardTitle>
              <CardDescription>Configure keyword, regex, and ML-based moderation rules</CardDescription>
            </CardHeader>
            <CardContent>
              <p className="text-center text-muted-foreground py-12">Rule builder UI (implement with drag-drop)</p>
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="analytics">
          <Card>
            <CardHeader>
              <CardTitle>Channel Analytics</CardTitle>
              <CardDescription>Growth, engagement, and retention metrics</CardDescription>
            </CardHeader>
            <CardContent>
              <p className="text-center text-muted-foreground py-12">Analytics charts (implement with Recharts/Visx)</p>
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="broadcast">
          <Card>
            <CardHeader>
              <CardTitle>Broadcast Messaging</CardTitle>
              <CardDescription>Send announcements to channels or user segments</CardDescription>
            </CardHeader>
            <CardContent>
              <div className="max-w-2xl space-y-4">
                <div>
                  <label className="block text-sm font-medium mb-2">Target Audience</label>
                  <Select>
                    <SelectTrigger><SelectValue placeholder="Select audience" /></SelectTrigger>
                    <SelectContent>
                      <SelectItem value="all">All Users</SelectItem>
                      <SelectItem value="channel">Specific Channel</SelectItem>
                      <SelectItem value="segment">User Segment</SelectItem>
                    </SelectContent>
                  </Select>
                </div>
                <div>
                  <label className="block text-sm font-medium mb-2">Message</label>
                  <textarea className="w-full min-h-[150px] p-4 border rounded-lg" placeholder="Enter your message..." />
                </div>
                <Button><Send className="h-4 w-4 mr-2" /> Send Broadcast</Button>
              </div>
            </CardContent>
          </Card>
        </TabsContent>
      </Tabs>
    </div>
  );
}

import { Settings, BarChart3, Send } from 'lucide-react';