/**
 * Pending Journeys Screen - List of detected journeys awaiting validation
 */

import React, {useState, useCallback} from 'react';
import {
  View,
  Text,
  FlatList,
  TouchableOpacity,
  StyleSheet,
  RefreshControl,
  Alert,
} from 'react-native';
import {useNavigation, useFocusEffect} from '@react-navigation/native';
import {NativeStackNavigationProp} from '@react-navigation/native-stack';
import tripDetection from '../native/TripDetection';
import {LocalJourney} from '../api/types';

type RootStackParamList = {
  PendingJourneys: undefined;
  PendingJourneyDetail: {journeyId: number};
};

type NavigationProp = NativeStackNavigationProp<
  RootStackParamList,
  'PendingJourneys'
>;

export default function PendingJourneysScreen(): JSX.Element {
  const navigation = useNavigation<NavigationProp>();
  const [journeys, setJourneys] = useState<LocalJourney[]>([]);
  const [refreshing, setRefreshing] = useState(false);
  const [isLoading, setIsLoading] = useState(true);

  useFocusEffect(
    useCallback(() => {
      loadJourneys();
    }, []),
  );

  const loadJourneys = async () => {
    try {
      const pending = await tripDetection.getPendingJourneys();
      setJourneys(pending);
    } catch (error) {
      console.error('Failed to load journeys:', error);
      Alert.alert('Erreur', 'Impossible de charger les trajets');
    } finally {
      setIsLoading(false);
    }
  };

  const onRefresh = async () => {
    setRefreshing(true);
    await loadJourneys();
    setRefreshing(false);
  };

  const handleDelete = async (id: number) => {
    Alert.alert(
      'Supprimer ce trajet?',
      'Cette action est irreversible.',
      [
        {text: 'Annuler', style: 'cancel'},
        {
          text: 'Supprimer',
          style: 'destructive',
          onPress: async () => {
            try {
              await tripDetection.deleteLocalJourney(id);
              setJourneys(prev => prev.filter(j => j.id !== id));
            } catch (error) {
              Alert.alert('Erreur', 'Impossible de supprimer le trajet');
            }
          },
        },
      ],
    );
  };

  const getTransportLabel = (type: string): string => {
    const labels: Record<string, string> = {
      marche: 'Marche',
      velo: 'Velo',
      voiture: 'Voiture',
      transport_commun: 'Transport en commun',
    };
    return labels[type] || type;
  };

  const getTransportIcon = (type: string): string => {
    const icons: Record<string, string> = {
      marche: '🚶',
      velo: '🚴',
      voiture: '🚗',
      transport_commun: '🚌',
    };
    return icons[type] || '🚶';
  };

  const formatDate = (timestamp: number): string => {
    const date = new Date(timestamp);
    return date.toLocaleDateString('fr-FR', {
      day: 'numeric',
      month: 'short',
      hour: '2-digit',
      minute: '2-digit',
    });
  };

  const renderJourney = ({item}: {item: LocalJourney}) => (
    <TouchableOpacity
      style={styles.journeyCard}
      onPress={() =>
        navigation.navigate('PendingJourneyDetail', {journeyId: item.id})
      }>
      <View style={styles.journeyHeader}>
        <Text style={styles.transportIcon}>
          {getTransportIcon(item.detectedTransportType)}
        </Text>
        <View style={styles.journeyInfo}>
          <Text style={styles.transportType}>
            {getTransportLabel(item.detectedTransportType)}
          </Text>
          <Text style={styles.journeyDate}>{formatDate(item.timeDeparture)}</Text>
        </View>
        <TouchableOpacity
          style={styles.deleteButton}
          onPress={() => handleDelete(item.id)}>
          <Text style={styles.deleteText}>X</Text>
        </TouchableOpacity>
      </View>

      <View style={styles.journeyDetails}>
        <View style={styles.detailItem}>
          <Text style={styles.detailLabel}>Duree</Text>
          <Text style={styles.detailValue}>{item.durationMinutes} min</Text>
        </View>
        <View style={styles.detailItem}>
          <Text style={styles.detailLabel}>Distance</Text>
          <Text style={styles.detailValue}>{item.distanceKm.toFixed(1)} km</Text>
        </View>
        <View style={styles.detailItem}>
          <Text style={styles.detailLabel}>Confiance</Text>
          <Text style={styles.detailValue}>{item.confidenceAvg}%</Text>
        </View>
      </View>

      <View style={styles.journeyFooter}>
        <Text style={styles.validateHint}>Appuyez pour valider et envoyer</Text>
      </View>
    </TouchableOpacity>
  );

  const renderEmpty = () => (
    <View style={styles.emptyContainer}>
      <Text style={styles.emptyIcon}>🚶</Text>
      <Text style={styles.emptyTitle}>Aucun trajet en attente</Text>
      <Text style={styles.emptySubtitle}>
        Activez la detection pour commencer a enregistrer vos trajets
      </Text>
    </View>
  );

  return (
    <View style={styles.container}>
      <FlatList
        data={journeys}
        renderItem={renderJourney}
        keyExtractor={item => item.id.toString()}
        contentContainerStyle={styles.listContent}
        refreshControl={
          <RefreshControl refreshing={refreshing} onRefresh={onRefresh} />
        }
        ListEmptyComponent={!isLoading ? renderEmpty : null}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#f5f5f5',
  },
  listContent: {
    padding: 16,
    flexGrow: 1,
  },
  journeyCard: {
    backgroundColor: '#fff',
    borderRadius: 12,
    padding: 16,
    marginBottom: 12,
    elevation: 2,
    shadowColor: '#000',
    shadowOffset: {width: 0, height: 1},
    shadowOpacity: 0.1,
    shadowRadius: 2,
  },
  journeyHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 12,
  },
  transportIcon: {
    fontSize: 32,
    marginRight: 12,
  },
  journeyInfo: {
    flex: 1,
  },
  transportType: {
    fontSize: 16,
    fontWeight: '600',
    color: '#333',
  },
  journeyDate: {
    fontSize: 14,
    color: '#666',
    marginTop: 2,
  },
  deleteButton: {
    width: 32,
    height: 32,
    borderRadius: 16,
    backgroundColor: '#FFEBEE',
    justifyContent: 'center',
    alignItems: 'center',
  },
  deleteText: {
    color: '#F44336',
    fontWeight: 'bold',
  },
  journeyDetails: {
    flexDirection: 'row',
    justifyContent: 'space-around',
    paddingVertical: 12,
    borderTopWidth: 1,
    borderTopColor: '#eee',
    borderBottomWidth: 1,
    borderBottomColor: '#eee',
  },
  detailItem: {
    alignItems: 'center',
  },
  detailLabel: {
    fontSize: 12,
    color: '#999',
  },
  detailValue: {
    fontSize: 16,
    fontWeight: '600',
    color: '#333',
    marginTop: 2,
  },
  journeyFooter: {
    marginTop: 12,
    alignItems: 'center',
  },
  validateHint: {
    fontSize: 13,
    color: '#2E7D32',
    fontWeight: '500',
  },
  emptyContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    paddingVertical: 60,
  },
  emptyIcon: {
    fontSize: 64,
    marginBottom: 16,
  },
  emptyTitle: {
    fontSize: 18,
    fontWeight: '600',
    color: '#333',
    marginBottom: 8,
  },
  emptySubtitle: {
    fontSize: 14,
    color: '#666',
    textAlign: 'center',
    paddingHorizontal: 32,
  },
});
