import { NodeSDK } from '@opentelemetry/sdk-node';
import { getNodeAutoInstrumentations } from '@opentelemetry/auto-instrumentations-node';
import { PrometheusExporter } from '@opentelemetry/exporter-prometheus';
import { Resource } from '@opentelemetry/resources';
import { SemanticResourceAttributes } from '@opentelemetry/semantic-conventions';
import { Config } from './config.js';
import pino from 'pino';

const logger = pino({ name: 'otel' });

let sdk: NodeSDK | null = null;
let prometheusExporter: PrometheusExporter | null = null;

export async function initTelemetry(config: Config): Promise<void> {
  if (!config.enableTracing) {
    logger.info('OpenTelemetry tracing disabled');
    return;
  }

  try {
    const resource = new Resource({
      [SemanticResourceAttributes.SERVICE_NAME]: config.otel?.serviceName || 'red-media-sfu',
      [SemanticResourceAttributes.SERVICE_VERSION]: '2.0.0',
      [SemanticResourceAttributes.DEPLOYMENT_ENVIRONMENT]: process.env.NODE_ENV || 'development',
    });

    // Initialize Prometheus exporter for metrics
    prometheusExporter = new PrometheusExporter(
      {
        port: 9464,
        endpoint: '/metrics',
      },
      () => {
        logger.info('Prometheus scrape endpoint: http://localhost:9464/metrics');
      }
    );

    await prometheusExporter.start();

    // Initialize OpenTelemetry SDK
    sdk = new NodeSDK({
      resource,
      instrumentations: [getNodeAutoInstrumentations()],
    });

    sdk.start();
    logger.info('OpenTelemetry initialized');

    // Graceful shutdown
    process.on('SIGTERM', async () => {
      await shutdownTelemetry();
    });
  } catch (error) {
    logger.error({ error }, 'Failed to initialize OpenTelemetry');
  }
}

export async function shutdownTelemetry(): Promise<void> {
  if (sdk) {
    try {
      await sdk.shutdown();
      logger.info('OpenTelemetry shut down');
    } catch (error) {
      logger.error({ error }, 'Error shutting down OpenTelemetry');
    }
  }
  if (prometheusExporter) {
    try {
      await prometheusExporter.shutdown();
    } catch (error) {
      logger.error({ error }, 'Error shutting down Prometheus exporter');
    }
  }
}

export function getPrometheusExporter(): PrometheusExporter | null {
  return prometheusExporter;
}