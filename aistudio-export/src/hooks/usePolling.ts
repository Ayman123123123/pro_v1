import { useEffect, useRef } from 'react';

/**
 * استطلاع دوري واعٍ بحالة التبويب.
 *
 * المشكلة التي يحلّها: صفحات اللوحة كانت تستخدم setInterval مباشرة
 * (المراقبة الحية كل 5 ثوانٍ، DINSTAR كل 5 ثوانٍ، الرئيسية كل 30 ثانية).
 * هذا المؤقّت يستمر حتى عندما يكون التبويب مخفيًا أو الجهاز غير متصل،
 * فيُغرق الخادم بطلبات لا يراها أحد ويستهلك رصيد استعلامات DINSTAR الحقيقي.
 *
 * السلوك الجديد:
 * - يتوقّف الاستطلاع عند إخفاء التبويب (document.hidden) ويستأنف فورًا عند العودة،
 *   مع تنفيذ فوري لتحديث البيانات القديمة.
 * - يتجاهل الدورة إذا كان المتصفح غير متصل بالشبكة (navigator.onLine).
 * - يمنع تداخل الطلبات: لا تبدأ دورة جديدة قبل انتهاء السابقة.
 * - يحتفظ بأحدث نسخة من الدالة دون إعادة تشغيل المؤقّت في كل تصيير.
 *
 * @param callback الدالة المنفَّذة كل دورة (قد تكون async).
 * @param intervalMs الفاصل الزمني بالمللي ثانية؛ مرِّر null لإيقاف الاستطلاع.
 * @param options.immediate تنفيذ فوري عند التركيب (افتراضيًا true).
 */
export function usePolling(
  callback: () => void | Promise<void>,
  intervalMs: number | null,
  options: { immediate?: boolean } = {}
): void {
  const { immediate = true } = options;
  const savedCallback = useRef(callback);
  const inFlight = useRef(false);

  useEffect(() => {
    savedCallback.current = callback;
  }, [callback]);

  useEffect(() => {
    if (intervalMs === null) return;

    let cancelled = false;
    let timer: ReturnType<typeof setInterval> | undefined;

    const run = async () => {
      if (cancelled || inFlight.current) return;
      if (typeof document !== 'undefined' && document.hidden) return;
      if (typeof navigator !== 'undefined' && navigator.onLine === false) return;
      inFlight.current = true;
      try {
        await savedCallback.current();
      } finally {
        inFlight.current = false;
      }
    };

    const start = () => {
      if (timer !== undefined) return;
      timer = setInterval(run, intervalMs);
    };

    const stop = () => {
      if (timer === undefined) return;
      clearInterval(timer);
      timer = undefined;
    };

    const onVisibilityChange = () => {
      if (document.hidden) {
        stop();
      } else {
        void run(); // تحديث فوري لأن البيانات المعروضة أصبحت قديمة
        start();
      }
    };

    if (immediate) void run();
    if (typeof document === 'undefined' || !document.hidden) start();
    document.addEventListener('visibilitychange', onVisibilityChange);

    return () => {
      cancelled = true;
      stop();
      document.removeEventListener('visibilitychange', onVisibilityChange);
    };
  }, [intervalMs, immediate]);
}

export default usePolling;
