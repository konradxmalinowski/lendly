type MapLibre = typeof import('maplibre-gl');

const STYLE_LIGHT = 'https://tiles.openfreemap.org/styles/positron';
const STYLE_DARK = 'https://tiles.openfreemap.org/styles/dark';
export const STYLE_TIMEOUT_MS = 10_000;

// Warsaw city centre, used only as the initial viewport when nothing better is known.
export const DEFAULT_CENTER: [number, number] = [21.0122, 52.2297];

let loading: Promise<MapLibre> | null = null;

export function mapStyleUrl(dark: boolean): string {
  return dark ? STYLE_DARK : STYLE_LIGHT;
}

/** Loads MapLibre, its worker, and its stylesheet only on routes that show a map. */
export function loadMapLibre(): Promise<MapLibre> {
  if (!loading) {
    loading = (async () => {
      ensureStylesheet();
      const maplibre = await import('maplibre-gl');
      maplibre.setWorkerUrl(new URL('/maplibre/maplibre-gl-worker.mjs', document.baseURI).href);
      return maplibre;
    })().catch((error: unknown) => {
      loading = null;
      throw error;
    });
  }
  return loading;
}

function ensureStylesheet(): void {
  if (document.querySelector('link[data-maplibre-css]')) {
    return;
  }
  const link = document.createElement('link');
  link.rel = 'stylesheet';
  link.href = 'maplibre.css';
  link.dataset['maplibreCss'] = '';
  document.head.appendChild(link);
}

export function supportsWebGl(): boolean {
  try {
    const canvas = document.createElement('canvas');
    return !!(canvas.getContext('webgl2') || canvas.getContext('webgl'));
  } catch {
    return false;
  }
}

export interface CreatedMap {
  maplibre: MapLibre;
  map: import('maplibre-gl').Map;
}

/** Creates a map and resolves once its style has loaded; rejects when WebGL, the style, or the tiles host is unavailable. */
export async function createMap(container: HTMLElement, center: [number, number], zoom: number, dark: boolean): Promise<CreatedMap> {
  if (!supportsWebGl()) {
    throw new Error('WebGL is unavailable.');
  }
  const maplibre = await loadMapLibre();
  const map = new maplibre.Map({
    container,
    style: mapStyleUrl(dark),
    center,
    zoom,
    attributionControl: { compact: true },
    cooperativeGestures: false
  });
  map.addControl(new maplibre.NavigationControl({ showCompass: false }), 'top-right');
  await new Promise<void>((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error('Map style timed out.')), STYLE_TIMEOUT_MS);
    map.once('load', () => {
      clearTimeout(timer);
      resolve();
    });
    map.once('error', (event) => {
      if (!map.isStyleLoaded()) {
        clearTimeout(timer);
        reject(event.error ?? new Error('Map failed to load.'));
      }
    });
  }).catch((error: unknown) => {
    map.remove();
    throw error;
  });
  return { maplibre, map };
}
