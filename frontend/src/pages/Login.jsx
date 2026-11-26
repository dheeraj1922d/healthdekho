import { useState } from 'react'
import api, { messageFromError } from '../api'
import Message from '../components/Message'

export default function Login({ onLogin }) {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [message, setMessage] = useState(null)
  const [busy, setBusy] = useState(false)

  async function submit(event) {
    event.preventDefault()
    setBusy(true)
    try {
      const { data } = await api.post('/auth/login', { email, password })
      onLogin(data.token, data.customer)
    } catch (error) {
      setMessage({ type: 'error', text: messageFromError(error) })
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="login card">
      <span className="brand">HealthDekho</span>
      <Message message={message} />
      <form onSubmit={submit}>
        <label>Email
          <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} required />
        </label>
        <label>Password
          <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} required />
        </label>
        <button className="btn" style={{ width: '100%' }} disabled={busy}>{busy ? 'Signing in...' : 'Sign in'}</button>
      </form>
    </div>
  )
}
