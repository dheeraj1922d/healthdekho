import { useState } from 'react'
import api, { messageFromError } from '../api'
import Message from '../components/Message'
import { money, formatDate } from '../format'

export default function Payments({ policies, onPaid }) {
  const [policyNumber, setPolicyNumber] = useState('')
  const [details, setDetails] = useState(null)
  const [amounts, setAmounts] = useState({})      // { installmentId: amountToPay } for ticked rows
  const [method, setMethod] = useState('UPI')
  const [message, setMessage] = useState(null)
  const [busy, setBusy] = useState(false)

  async function loadPolicy(number) {
    setPolicyNumber(number)
    setDetails(null)
    setAmounts({})
    if (!number) return
    try {
      const { data } = await api.get(`/policies/${encodeURIComponent(number)}`)
      setDetails(data)
    } catch (error) {
      setMessage({ type: 'error', text: messageFromError(error) })
    }
  }

  function toggle(installment) {
    const next = { ...amounts }
    if (next[installment.id] !== undefined) delete next[installment.id]
    else next[installment.id] = installment.remainingAmount
    setAmounts(next)
  }

  async function pay(event) {
    event.preventDefault()
    setBusy(true)
    try {
      const payments = Object.entries(amounts).map(([id, amount]) => ({ installmentId: Number(id), amount: Number(amount) }))
      const { data } = await api.post(`/policies/${encodeURIComponent(policyNumber)}/payments`, { payments, paymentMethod: method })
      setMessage({ type: 'success', text: `${data.length} payment(s) recorded.` })
      await loadPolicy(policyNumber)
      onPaid()
    } catch (error) {
      setMessage({ type: 'error', text: messageFromError(error) })
    } finally {
      setBusy(false)
    }
  }

  return (
    <>
      <h1>Premium payments</h1>
      <p className="muted">Payments are only recorded here - no real money moves. A 10% penalty applies after the one-month grace period.</p>
      <Message message={message} />

      <div className="card">
        <label>Choose a policy
          <select value={policyNumber} onChange={(e) => loadPolicy(e.target.value)}>
            <option value="">Select...</option>
            {policies.map((p) => <option key={p.policyNumber} value={p.policyNumber}>{p.policyName} ({p.policyNumber})</option>)}
          </select>
        </label>
      </div>

      {details && (
        <>
          <form className="card" onSubmit={pay}>
            <h3 style={{ marginTop: 0 }}>Installments</h3>
            <div className="table-wrap">
              <table>
                <thead><tr><th>Pay</th><th>#</th><th>Due</th><th>Grace ends</th><th>Amount</th><th>Paid</th><th>Balance</th><th>Status</th><th>Pay now</th></tr></thead>
                <tbody>
                  {details.installments.map((i) => {
                    const payable = Number(i.remainingAmount) > 0
                    const ticked = amounts[i.id] !== undefined
                    return (
                      <tr key={i.id}>
                        <td><input type="checkbox" style={{ width: 'auto' }} disabled={!payable} checked={ticked} onChange={() => toggle(i)} /></td>
                        <td>{i.installmentNumber}</td>
                        <td>{formatDate(i.dueDate)}</td>
                        <td>{formatDate(i.gracePeriodEndDate)}</td>
                        <td>{money(i.scheduledAmount)}</td>
                        <td>{money(i.paidAmount)}</td>
                        <td>{money(i.remainingAmount)}</td>
                        <td><span className={`tag ${i.paymentStatus}`}>{i.paymentStatus}</span></td>
                        <td>{ticked && (
                          <input type="number" step="0.01" min="0.01" max={i.remainingAmount} style={{ width: 110 }}
                                 value={amounts[i.id]} onChange={(e) => setAmounts({ ...amounts, [i.id]: e.target.value })} />
                        )}</td>
                      </tr>
                    )
                  })}
                </tbody>
              </table>
            </div>

            {Object.keys(amounts).length > 0 && (
              <div className="row" style={{ marginTop: 16 }}>
                <label>Payment method
                  <select value={method} onChange={(e) => setMethod(e.target.value)}>
                    <option value="UPI">UPI</option>
                    <option value="CARD">Card</option>
                    <option value="NET_BANKING">Net banking</option>
                    <option value="BANK_TRANSFER">Bank transfer</option>
                  </select>
                </label>
                <button className="btn" disabled={busy}>{busy ? 'Saving...' : 'Record payment'}</button>
              </div>
            )}
          </form>

          {details.paymentHistory.length > 0 && (
            <div className="card table-wrap">
              <h3 style={{ marginTop: 0 }}>Payment history</h3>
              <table>
                <thead><tr><th>Reference</th><th>Date</th><th>Premium</th><th>Penalty</th><th>Total</th><th>Method</th></tr></thead>
                <tbody>
                  {details.paymentHistory.map((p) => (
                    <tr key={p.transactionReference}>
                      <td>{p.transactionReference}</td><td>{formatDate(p.paymentDate)}</td>
                      <td>{money(p.principalAmount)}</td><td>{money(p.penaltyAmount)}</td>
                      <td>{money(p.chargedAmount)}</td><td>{p.paymentMethod}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </>
      )}
    </>
  )
}
