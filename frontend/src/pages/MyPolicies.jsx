import { money, formatDate, frequencyLabel, termLabel } from '../format'

export default function MyPolicies({ policies }) {
  const totalCover = policies.reduce((sum, p) => sum + Number(p.sumInsured), 0)
  const remaining = policies.reduce((sum, p) => sum + Number(p.remainingPremium), 0)

  return (
    <>
      <h1>My policies</h1>
      <p className="muted">Your cover and premium progress in one place.</p>

      <div className="stats">
        <div className="card stat"><span className="small">Policies</span><strong>{policies.length}</strong></div>
        <div className="card stat"><span className="small">Total cover</span><strong>{money(totalCover)}</strong></div>
        <div className="card stat"><span className="small">Premium remaining</span><strong>{money(remaining)}</strong></div>
      </div>

      <div className="card table-wrap">
        {policies.length === 0 ? <p>You have no policies yet. Open the "Plans" tab to register one.</p> : (
          <table>
            <thead>
              <tr><th>Policy</th><th>Type</th><th>Term</th><th>Starts</th><th>Matures</th><th>Premium</th><th>Cover</th><th>Payment</th></tr>
            </thead>
            <tbody>
              {policies.map((p) => (
                <tr key={p.policyNumber}>
                  <td><strong>{p.policyName}</strong><br /><span className="small">{p.policyNumber}</span></td>
                  <td>{p.policyType}</td>
                  <td>{termLabel(p.termMonths)}</td>
                  <td>{formatDate(p.startDate)}</td>
                  <td>{formatDate(p.maturityDate)}</td>
                  <td>{money(p.premiumAmount)}<br /><span className="small">{frequencyLabel(p.premiumFrequency)}</span></td>
                  <td>{money(p.sumInsured)}</td>
                  <td><span className={`tag ${p.paymentStatus}`}>{p.paymentStatus}</span></td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </>
  )
}
