import { useState } from 'react'
import api, { messageFromError } from '../api'
import Message from '../components/Message'
import { money, formatDate } from '../format'

// Search your own policies by policy number, name and/or date of birth.
export default function Search() {
  const [form, setForm] = useState({ policyNumber: '', customerName: '', dateOfBirth: '' })
  const [results, setResults] = useState(null)
  const [message, setMessage] = useState(null)

  const update = (field) => (e) => setForm({ ...form, [field]: e.target.value })

  async function submit(event) {
    event.preventDefault()
    setMessage(null)
    try {
      const params = Object.fromEntries(Object.entries(form).filter(([, value]) => value.trim()))
      const { data } = await api.get('/policies/search', { params })
      setResults(data)
    } catch (error) {
      setResults(null)
      setMessage({ type: 'error', text: messageFromError(error) })
    }
  }

  return (
    <>
      <h1>Search policies</h1>
      <p className="muted">Fill in at least one field.</p>
      <Message message={message} />

      <form className="card" onSubmit={submit}>
        <div className="row">
          <label>Policy number<input value={form.policyNumber} onChange={update('policyNumber')} placeholder="HDP-2026-0001" /></label>
          <label>Customer name<input value={form.customerName} onChange={update('customerName')} /></label>
          <label>Date of birth<input type="date" value={form.dateOfBirth} onChange={update('dateOfBirth')} /></label>
          <label style={{ flex: 'none', minWidth: 0 }}><button className="btn">Search</button></label>
        </div>
      </form>

      {results && (
        <div className="card table-wrap">
          {results.length === 0 ? <p>No policies matched.</p> : (
            <table>
              <thead><tr><th>Policy</th><th>Customer</th><th>Date of birth</th><th>Cover</th><th>Paid</th><th>Remaining</th><th>Status</th></tr></thead>
              <tbody>
                {results.map((p) => (
                  <tr key={p.policyNumber}>
                    <td><strong>{p.policyName}</strong><br /><span className="small">{p.policyNumber}</span></td>
                    <td>{p.customerName}</td><td>{formatDate(p.dateOfBirth)}</td>
                    <td>{money(p.sumInsured)}</td><td>{money(p.totalPremiumPaid)}</td><td>{money(p.remainingPremium)}</td>
                    <td><span className={`tag ${p.paymentStatus}`}>{p.paymentStatus}</span></td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      )}
    </>
  )
}
