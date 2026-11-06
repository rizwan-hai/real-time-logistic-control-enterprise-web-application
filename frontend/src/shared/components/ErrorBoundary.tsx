import { Component, type ErrorInfo, type ReactNode } from 'react';
import { Button, Result } from 'antd';

interface State { hasError: boolean }
export class ErrorBoundary extends Component<{ children: ReactNode }, State> {
  state: State = { hasError: false };
  static getDerivedStateFromError(): State { return { hasError: true }; }
  componentDidCatch(error: Error, info: ErrorInfo) { console.error('Frontend render error', error, info.componentStack); }
  render() {
    if (this.state.hasError) return <Result status="error" title="The page could not be displayed" extra={<Button onClick={() => window.location.reload()}>Reload</Button>} />;
    return this.props.children;
  }
}
