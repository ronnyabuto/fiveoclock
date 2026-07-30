import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import App from './App'

describe('application shell', () => {
  it('renders one place with digital and analogue time', () => {
    render(<App />)
    expect(screen.getByRole('heading')).toBeTruthy()
    expect(screen.getByRole('img', { name: /analogue clock/i })).toBeTruthy()
    expect(screen.getByText(/closest to 5 pm/i)).toBeTruthy()
  })
})
