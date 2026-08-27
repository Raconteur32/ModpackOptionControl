import '@testing-library/jest-dom/vitest'

// jsdom ne fournit ni scrollIntoView ni matchMedia (Zag / Chakra en ont besoin)
Element.prototype.scrollIntoView = Element.prototype.scrollIntoView ?? (() => {})

// ResizeObserver : requis par le positionnement floating-ui (Menu, Popover).
if (!('ResizeObserver' in globalThis)) {
  globalThis.ResizeObserver = class {
    observe() {}
    unobserve() {}
    disconnect() {}
  } as unknown as typeof ResizeObserver
}

if (!window.matchMedia) {
  window.matchMedia = ((query: string) => ({
    matches: false,
    media: query,
    onchange: null,
    addListener: () => {},
    removeListener: () => {},
    addEventListener: () => {},
    removeEventListener: () => {},
    dispatchEvent: () => false,
  })) as unknown as typeof window.matchMedia
}
