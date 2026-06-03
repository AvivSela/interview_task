import '@testing-library/jest-dom';

vi.mock('qrcode');

if (!vi.runAllMicrotasksAsync) {
  vi.runAllMicrotasksAsync = async () => {
    await Promise.resolve();
    await Promise.resolve();
    await Promise.resolve();
  };
}
