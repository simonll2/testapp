/**
 * Pending Journey Detail Screen - Edit and validate a journey
 */

import React, {useState, useEffect} from 'react';
import {
  View,
  Text,
  TextInput,
  TouchableOpacity,
  StyleSheet,
  Alert,
  ScrollView,
  ActivityIndicator,
  Modal,
} from 'react-native';
import {useNavigation, useRoute, RouteProp} from '@react-navigation/native';
import tripDetection from '../native/TripDetection';
import {apiClient} from '../api/client';
import {LocalJourney, TransportType, JourneyCreate} from '../api/types';

type RootStackParamList = {
  PendingJourneyDetail: {journeyId: number};
};

type RouteType = RouteProp<RootStackParamList, 'PendingJourneyDetail'>;

const TRANSPORT_OPTIONS: {value: TransportType; label: string; icon: string}[] =
  [
    {value: 'marche', label: 'Marche', icon: '🚶'},
    {value: 'velo', label: 'Velo', icon: '🚴'},
    {value: 'transport_commun', label: 'Transport en commun', icon: '🚌'},
    {value: 'voiture', label: 'Voiture', icon: '🚗'},
  ];

export default function PendingJourneyDetailScreen(): JSX.Element {
  const navigation = useNavigation();
  const route = useRoute<RouteType>();
  const {journeyId} = route.params;

  const [journey, setJourney] = useState<LocalJourney | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isSending, setIsSending] = useState(false);
  const [showReward, setShowReward] = useState(false);
  const [rewardScore, setRewardScore] = useState(0);

  // Editable fields
  const [transportType, setTransportType] = useState<TransportType>('marche');
  const [distanceKm, setDistanceKm] = useState('');
  const [placeDeparture, setPlaceDeparture] = useState('');
  const [placeArrival, setPlaceArrival] = useState('');

  useEffect(() => {
    loadJourney();
  }, [journeyId]);

  const loadJourney = async () => {
    try {
      const data = await tripDetection.getJourney(journeyId);
      setJourney(data);

      // Initialize editable fields
      setTransportType(data.detectedTransportType as TransportType);
      setDistanceKm(data.distanceKm.toFixed(2));
      setPlaceDeparture(data.placeDeparture);
      setPlaceArrival(data.placeArrival);
    } catch (error) {
      Alert.alert('Erreur', 'Impossible de charger le trajet');
      navigation.goBack();
    } finally {
      setIsLoading(false);
    }
  };

  const handleValidateAndSend = async () => {
    if (!journey) return;

    const distance = parseFloat(distanceKm);
    if (isNaN(distance) || distance <= 0) {
      Alert.alert('Erreur', 'Veuillez entrer une distance valide');
      return;
    }

    if (!placeDeparture.trim() || !placeArrival.trim()) {
      Alert.alert('Erreur', 'Veuillez renseigner les lieux de depart et arrivee');
      return;
    }

    setIsSending(true);

    try {
      // Update local journey first
      await tripDetection.updateLocalJourney(journey.id, {
        transportType,
        distanceKm: distance,
        placeDeparture: placeDeparture.trim(),
        placeArrival: placeArrival.trim(),
      });

      // Prepare journey for backend
      const journeyCreate: JourneyCreate = {
        place_departure: placeDeparture.trim(),
        place_arrival: placeArrival.trim(),
        time_departure: new Date(journey.timeDeparture).toISOString(),
        time_arrival: new Date(journey.timeArrival).toISOString(),
        distance_km: distance,
        transport_type: transportType,
        detection_source: 'auto',
      };

      // Send to backend
      const result = await apiClient.createJourney(journeyCreate);

      // Mark as sent locally
      await tripDetection.markJourneySent(journey.id);

      // Show reward
      setRewardScore(result.score_journey);
      setShowReward(true);
    } catch (error) {
      const message =
        error instanceof Error ? error.message : 'Erreur inconnue';
      Alert.alert('Erreur', `Impossible d'envoyer le trajet: ${message}`);
    } finally {
      setIsSending(false);
    }
  };

  const handleCloseReward = () => {
    setShowReward(false);
    navigation.goBack();
  };

  const formatDateTime = (timestamp: number): string => {
    return new Date(timestamp).toLocaleString('fr-FR', {
      day: 'numeric',
      month: 'long',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    });
  };

  if (isLoading) {
    return (
      <View style={styles.loadingContainer}>
        <ActivityIndicator size="large" color="#2E7D32" />
      </View>
    );
  }

  if (!journey) {
    return (
      <View style={styles.loadingContainer}>
        <Text>Trajet non trouve</Text>
      </View>
    );
  }

  return (
    <ScrollView style={styles.container}>
      {/* Journey Info */}
      <View style={styles.section}>
        <Text style={styles.sectionTitle}>Informations du trajet</Text>
        <View style={styles.infoCard}>
          <View style={styles.infoRow}>
            <Text style={styles.infoLabel}>Depart</Text>
            <Text style={styles.infoValue}>
              {formatDateTime(journey.timeDeparture)}
            </Text>
          </View>
          <View style={styles.infoRow}>
            <Text style={styles.infoLabel}>Arrivee</Text>
            <Text style={styles.infoValue}>
              {formatDateTime(journey.timeArrival)}
            </Text>
          </View>
          <View style={styles.infoRow}>
            <Text style={styles.infoLabel}>Duree</Text>
            <Text style={styles.infoValue}>{journey.durationMinutes} min</Text>
          </View>
          <View style={styles.infoRow}>
            <Text style={styles.infoLabel}>Confiance detection</Text>
            <Text style={styles.infoValue}>{journey.confidenceAvg}%</Text>
          </View>
        </View>
      </View>

      {/* Transport Type */}
      <View style={styles.section}>
        <Text style={styles.sectionTitle}>Mode de transport</Text>
        <View style={styles.transportGrid}>
          {TRANSPORT_OPTIONS.map(option => (
            <TouchableOpacity
              key={option.value}
              style={[
                styles.transportOption,
                transportType === option.value && styles.transportOptionSelected,
              ]}
              onPress={() => setTransportType(option.value)}>
              <Text style={styles.transportIcon}>{option.icon}</Text>
              <Text
                style={[
                  styles.transportLabel,
                  transportType === option.value &&
                    styles.transportLabelSelected,
                ]}>
                {option.label}
              </Text>
            </TouchableOpacity>
          ))}
        </View>
      </View>

      {/* Editable Fields */}
      <View style={styles.section}>
        <Text style={styles.sectionTitle}>Details</Text>

        <Text style={styles.inputLabel}>Distance (km)</Text>
        <TextInput
          style={styles.input}
          value={distanceKm}
          onChangeText={setDistanceKm}
          keyboardType="decimal-pad"
          placeholder="0.00"
        />

        <Text style={styles.inputLabel}>Lieu de depart</Text>
        <TextInput
          style={styles.input}
          value={placeDeparture}
          onChangeText={setPlaceDeparture}
          placeholder="Ex: Domicile, Gare de Lyon..."
        />

        <Text style={styles.inputLabel}>Lieu d'arrivee</Text>
        <TextInput
          style={styles.input}
          value={placeArrival}
          onChangeText={setPlaceArrival}
          placeholder="Ex: Bureau, Centre commercial..."
        />
      </View>

      {/* Submit Button */}
      <TouchableOpacity
        style={[styles.submitButton, isSending && styles.submitButtonDisabled]}
        onPress={handleValidateAndSend}
        disabled={isSending}>
        {isSending ? (
          <ActivityIndicator color="#fff" />
        ) : (
          <Text style={styles.submitButtonText}>Valider et envoyer</Text>
        )}
      </TouchableOpacity>

      {/* Reward Modal */}
      <Modal visible={showReward} transparent animationType="fade">
        <View style={styles.modalOverlay}>
          <View style={styles.rewardCard}>
            <Text style={styles.rewardIcon}>🎉</Text>
            <Text style={styles.rewardTitle}>Felicitations!</Text>
            <Text style={styles.rewardScore}>+{rewardScore} points</Text>
            <Text style={styles.rewardMessage}>
              Votre trajet a ete valide et enregistre.
            </Text>
            <TouchableOpacity
              style={styles.rewardButton}
              onPress={handleCloseReward}>
              <Text style={styles.rewardButtonText}>Continuer</Text>
            </TouchableOpacity>
          </View>
        </View>
      </Modal>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#f5f5f5',
  },
  loadingContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    backgroundColor: '#f5f5f5',
  },
  section: {
    padding: 16,
  },
  sectionTitle: {
    fontSize: 16,
    fontWeight: '600',
    color: '#333',
    marginBottom: 12,
  },
  infoCard: {
    backgroundColor: '#fff',
    borderRadius: 12,
    padding: 16,
  },
  infoRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    paddingVertical: 8,
    borderBottomWidth: 1,
    borderBottomColor: '#f0f0f0',
  },
  infoLabel: {
    fontSize: 14,
    color: '#666',
  },
  infoValue: {
    fontSize: 14,
    fontWeight: '500',
    color: '#333',
  },
  transportGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 12,
  },
  transportOption: {
    flex: 1,
    minWidth: '45%',
    backgroundColor: '#fff',
    borderRadius: 12,
    padding: 16,
    alignItems: 'center',
    borderWidth: 2,
    borderColor: 'transparent',
  },
  transportOptionSelected: {
    borderColor: '#2E7D32',
    backgroundColor: '#E8F5E9',
  },
  transportIcon: {
    fontSize: 32,
    marginBottom: 8,
  },
  transportLabel: {
    fontSize: 14,
    color: '#666',
  },
  transportLabelSelected: {
    color: '#2E7D32',
    fontWeight: '600',
  },
  inputLabel: {
    fontSize: 14,
    color: '#666',
    marginBottom: 6,
    marginTop: 12,
  },
  input: {
    backgroundColor: '#fff',
    borderRadius: 8,
    paddingHorizontal: 16,
    paddingVertical: 12,
    fontSize: 16,
    borderWidth: 1,
    borderColor: '#ddd',
    color: '#333',
  },
  submitButton: {
    backgroundColor: '#2E7D32',
    borderRadius: 12,
    paddingVertical: 16,
    marginHorizontal: 16,
    marginVertical: 24,
    alignItems: 'center',
  },
  submitButtonDisabled: {
    backgroundColor: '#9E9E9E',
  },
  submitButtonText: {
    color: '#fff',
    fontSize: 16,
    fontWeight: '600',
  },
  modalOverlay: {
    flex: 1,
    backgroundColor: 'rgba(0,0,0,0.5)',
    justifyContent: 'center',
    alignItems: 'center',
    padding: 24,
  },
  rewardCard: {
    backgroundColor: '#fff',
    borderRadius: 20,
    padding: 32,
    alignItems: 'center',
    width: '100%',
    maxWidth: 320,
  },
  rewardIcon: {
    fontSize: 64,
    marginBottom: 16,
  },
  rewardTitle: {
    fontSize: 24,
    fontWeight: 'bold',
    color: '#333',
    marginBottom: 8,
  },
  rewardScore: {
    fontSize: 36,
    fontWeight: 'bold',
    color: '#2E7D32',
    marginBottom: 8,
  },
  rewardMessage: {
    fontSize: 14,
    color: '#666',
    textAlign: 'center',
    marginBottom: 24,
  },
  rewardButton: {
    backgroundColor: '#2E7D32',
    borderRadius: 12,
    paddingVertical: 14,
    paddingHorizontal: 32,
  },
  rewardButtonText: {
    color: '#fff',
    fontSize: 16,
    fontWeight: '600',
  },
});
