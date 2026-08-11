import React from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import App from './App';
import './styles.css';

const container = document.getElementById('root');
// فشل صريح ومفهوم بدل انهيار غامض إذا لم يوجد عنصر التركيب،
// وهو أيضًا ما يجعل التحقق الصارم بـ TypeScript يمر بلا تحذير.
if (!container) {
  throw new Error('عنصر التركيب #root غير موجود في index.html');
}
const root = createRoot(container);

root.render(
  <React.StrictMode>
    <BrowserRouter>
      <App />
    </BrowserRouter>
  </React.StrictMode>
);
