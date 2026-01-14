/**
 * TypeScript wrapper for the native TripDetectionModule
 */

import {NativeModules, NativeEventEmitter, Platform} from 'react-native';
import {LocalJourney, PermissionStatus} from '../api/types';

const {TripDetectionModule} = NativeModules;

// Event emitter for native events
const eventEmitter = Platform.OS === 'android'
  ? new NativeEventEmitter(TripDetectionModule)
  : null;

// Event types
export const EVENTS = {
  TRIP_DETECTED: 'onTripDetected',
  DETECTION_STATE_CHANGED: 'onDetectionStateChanged',
};

/**
 * Trip Detection Native Module Interface
 */
class TripDetection {
  /**
   * Start the trip detection service
   */
  async startDetection(): Promise<boolean> {
    if (Platform.OS !== 'android') {
      console.warn('Trip detection is only available on Android');
      return false;
    }
    return TripDetectionModule.startDetection();
  }

  /**
   * Stop the trip detection service
   */
  async stopDetection(): Promise<boolean> {
    if (Platform.OS !== 'android') {
      return false;
    }
    return TripDetectionModule.stopDetection();
  }

  /**
   * Check if detection service is running
   */
  async isDetectionRunning(): Promise<boolean> {
    if (Platform.OS !== 'android') {
      return false;
    }
    return TripDetectionModule.isDetectionRunning();
  }

  /**
   * Get all pending (not yet sent) journeys
   */
  async getPendingJourneys(): Promise<LocalJourney[]> {
    if (Platform.OS !== 'android') {
      return [];
    }
    return TripDetectionModule.getPendingJourneys();
  }

  /**
   * Get a specific journey by ID
   */
  async getJourney(id: number): Promise<LocalJourney> {
    if (Platform.OS !== 'android') {
      throw new Error('Not available on this platform');
    }
    return TripDetectionModule.getJourney(id);
  }

  /**
   * Update a local journey
   */
  async updateLocalJourney(
    id: number,
    updates: Partial<{
      transportType: string;
      distanceKm: number;
      placeDeparture: string;
      placeArrival: string;
    }>,
  ): Promise<boolean> {
    if (Platform.OS !== 'android') {
      return false;
    }
    return TripDetectionModule.updateLocalJourney(id, updates);
  }

  /**
   * Delete a local journey
   */
  async deleteLocalJourney(id: number): Promise<boolean> {
    if (Platform.OS !== 'android') {
      return false;
    }
    return TripDetectionModule.deleteLocalJourney(id);
  }

  /**
   * Mark a journey as sent to backend
   */
  async markJourneySent(id: number): Promise<boolean> {
    if (Platform.OS !== 'android') {
      return false;
    }
    return TripDetectionModule.markJourneySent(id);
  }

  /**
   * Get count of pending journeys
   */
  async getPendingCount(): Promise<number> {
    if (Platform.OS !== 'android') {
      return 0;
    }
    return TripDetectionModule.getPendingCount();
  }

  /**
   * Check permission status
   */
  async checkPermissions(): Promise<PermissionStatus> {
    if (Platform.OS !== 'android') {
      return {
        activityRecognition: false,
        notifications: false,
        allGranted: false,
      };
    }
    return TripDetectionModule.checkPermissions();
  }

  /**
   * Request required permissions
   */
  async requestPermissions(): Promise<boolean> {
    if (Platform.OS !== 'android') {
      return false;
    }
    return TripDetectionModule.requestPermissions();
  }

  /**
   * Enable or disable native debug/demo mode (POC)
   */
  async setDebugMode(enabled: boolean): Promise<void> {
    if (Platform.OS !== 'android') return;
    return TripDetectionModule.setDebugMode(enabled);
  }

  /**
   * Ask native side to create a simulated trip (used for testing full app workflow)
   */
  async simulateTrip(): Promise<void> {
    if (Platform.OS !== 'android') return;
    return TripDetectionModule.simulateTrip();
  }

  /**
   * Subscribe to trip detection events
   */
  addTripDetectedListener(
    callback: (journey: LocalJourney) => void,
  ): (() => void) | null {
    if (!eventEmitter) {
      return null;
    }
    const subscription = eventEmitter.addListener(
      EVENTS.TRIP_DETECTED,
      callback,
    );
    return () => subscription.remove();
  }

  /**
   * Subscribe to detection state changes
   */
  addStateChangeListener(
    callback: (state: {isRunning: boolean}) => void,
  ): (() => void) | null {
    if (!eventEmitter) {
      return null;
    }
    const subscription = eventEmitter.addListener(
      EVENTS.DETECTION_STATE_CHANGED,
      callback,
    );
    return () => subscription.remove();
  }
}

export const tripDetection = new TripDetection();
export default tripDetection;
