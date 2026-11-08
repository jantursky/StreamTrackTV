import type { MailConfig } from '../env'
import type { Digest } from './digest'

const RESEND_ENDPOINT = 'https://api.resend.com/emails'

/** Sends one email through the Resend HTTP API with plain fetch; throws on a non-2xx answer. */
export async function sendMail(
  config: MailConfig,
  to: string,
  digest: Digest,
  fetchImpl: typeof fetch = fetch,
): Promise<void> {
  const response = await fetchImpl(RESEND_ENDPOINT, {
    method: 'POST',
    headers: { Authorization: `Bearer ${config.apiKey}`, 'Content-Type': 'application/json' },
    body: JSON.stringify({
      from: config.from,
      to: [to],
      subject: digest.subject,
      text: digest.text,
      html: digest.html,
    }),
  })
  if (!response.ok) throw new Error(`Mail provider answered ${response.status}`)
}
