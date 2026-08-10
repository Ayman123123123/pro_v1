import React from 'react';
import { Tabs } from 'antd';
import AuthorityTab from './tabs/AuthorityTab';
import PstnAccessTab from './tabs/PstnAccessTab';

export default function UserManagement() {
  return <Tabs defaultActiveKey="approval" items={[
    { key:'approval', label:'طلبات الموافقة', children:<AuthorityTab/> },
    { key:'pstn', label:'صلاحيات الاتصال اليمني', children:<PstnAccessTab/> }
  ]} />;
}
