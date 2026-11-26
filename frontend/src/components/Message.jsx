// Shows a green (success) or red (error) banner. Pass message = { type: 'success' | 'error', text }.
export default function Message({ message }) {
  if (!message) return null
  return <div className={`message ${message.type === 'error' ? 'error' : ''}`}>{message.text}</div>
}
