export const resources = {
  en: { translation: {
    appName: 'Logistics Control', welcome: 'Welcome back', dashboard: 'Dashboard', overview: 'Overview',
    staff: 'Staff workspace', customer: 'Customer portal', driver: 'Driver workspace', publicTracking: 'Public tracking',
    orders: 'Orders', shipments: 'Shipments', dispatch: 'Dispatch', warehouse: 'Warehouse', users: 'Users', roles: 'Roles',
    settings: 'Settings', notifications: 'Notifications', globalSearch: 'Search orders, shipments, customers…',
    profile: 'Profile', signOut: 'Sign out', language: 'Language', lightTheme: 'Light theme', darkTheme: 'Dark theme',
    signIn: 'Sign in', emailOrPhone: 'Email or phone', password: 'Password', forgotPassword: 'Forgot password?',
    sendResetLink: 'Send reset link', resetLinkSent: 'If the account is eligible, a reset link has been sent.',
    backToSignIn: 'Back to sign in', accessDenied: 'Access denied', noPermission: 'Your account does not have permission to view this page.',
    notFound: 'The page could not be found.', save: 'Save', cancel: 'Cancel',
    emptyTitle: 'Nothing here yet', emptyDescription: 'When information is available, it will appear here.',
    errorTitle: 'Something went wrong', retry: 'Try again', publicTrackingTitle: 'Track a shipment', trackingCode: 'Tracking code',
    recentActivity: 'Recent activity', quickActions: 'Quick actions', viewAll: 'View all',
  } },
  fa: { translation: {
    appName: 'کنترل لجستیک', welcome: 'خوش آمدید', dashboard: 'داشبورد', overview: 'نمای کلی',
    staff: 'محیط کاری کارکنان', customer: 'درگاه مشتری', driver: 'محیط کاری راننده', publicTracking: 'پیگیری عمومی',
    orders: 'سفارش‌ها', shipments: 'محموله‌ها', dispatch: 'اعزام', warehouse: 'انبار', users: 'کاربران', roles: 'نقش‌ها',
    settings: 'تنظیمات', notifications: 'اعلان‌ها', globalSearch: 'جستجوی سفارش، محموله و مشتری…',
    profile: 'نمایه', signOut: 'خروج', language: 'زبان', lightTheme: 'پوسته روشن', darkTheme: 'پوسته تاریک',
    signIn: 'ورود', emailOrPhone: 'ایمیل یا شماره تلفن', password: 'گذرواژه', forgotPassword: 'گذرواژه را فراموش کردید؟',
    sendResetLink: 'ارسال پیوند بازنشانی', resetLinkSent: 'در صورت واجد شرایط بودن حساب، پیوند بازنشانی ارسال می‌شود.',
    backToSignIn: 'بازگشت به ورود', accessDenied: 'دسترسی رد شد', noPermission: 'حساب شما اجازه مشاهده این صفحه را ندارد.',
    notFound: 'صفحه پیدا نشد.', save: 'ذخیره', cancel: 'لغو',
    emptyTitle: 'هنوز چیزی وجود ندارد', emptyDescription: 'اطلاعات پس از آماده شدن در اینجا نمایش داده می‌شود.',
    errorTitle: 'مشکلی پیش آمد', retry: 'تلاش دوباره', publicTrackingTitle: 'پیگیری محموله', trackingCode: 'کد پیگیری',
    recentActivity: 'فعالیت‌های اخیر', quickActions: 'دسترسی سریع', viewAll: 'نمایش همه',
  } },
} as const;

export type Locale = keyof typeof resources;
