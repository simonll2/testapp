/**
 * API Types for Green Mobility Pass
 */

// Transport types accepted by the backend
export type TransportType = 'apied' | 'velo' | 'transport_commun' | 'voiture';

// Detection source
export type DetectionSource = 'auto' | 'manual';

// Journey creation request
export interface JourneyCreate {
  place_departure: string;
  place_arrival: string;
  time_departure: string; // ISO datetime
  time_arrival: string; // ISO datetime
  distance_km: number;
  transport_type: TransportType;
  detection_source: DetectionSource;
}

// Journey response from backend
export interface JourneyRead {
  id: number;
  user_id: number;
  place_departure: string;
  place_arrival: string;
  time_departure: string;
  time_arrival: string;
  duration_minutes: number;
  distance_km: number;
  transport_type: TransportType;
  detection_source: DetectionSource;
  score_journey: number;
  created_at: string;
}

// Token response
export interface TokenResponse {
  access_token: string;
  refresh_token: string;
  token_type: string;
}

// User info
export interface UserInfo {
  id: number;
  email: string;
  username: string;
  is_active: boolean;
}

// User statistics
export interface UserStatistics {
  total_journeys: number;
  total_distance_km: number;
  total_score: number;
  journeys_by_transport: {
    [key in TransportType]?: number;
  };
}

// Local journey from native module
export interface LocalJourney {
  id: number;
  timeDeparture: number; // epoch ms
  timeArrival: number; // epoch ms
  durationMinutes: number;
  distanceKm: number;
  detectedTransportType: string;
  confidenceAvg: number;
  placeDeparture: string;
  placeArrival: string;
  status: 'PENDING' | 'SENT';
  createdAt: number;
  updatedAt: number;
}

// Permission status
export interface PermissionStatus {
  activityRecognition: boolean;
  notifications: boolean;
  allGranted: boolean;
}
