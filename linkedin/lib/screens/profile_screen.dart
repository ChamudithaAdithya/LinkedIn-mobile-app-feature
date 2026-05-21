import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../providers/search_provider.dart';

class ProfileScreen extends ConsumerWidget {
  const ProfileScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final state = ref.watch(searchProvider);
    final candidate = state.selectedCandidate;

    return Scaffold(
      appBar: AppBar(title: const Text('Selected Profile')),
      body: Padding(
        padding: const EdgeInsets.all(16),
        child: candidate == null
            ? const Center(child: Text('No profile selected yet.'))
            : SingleChildScrollView(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Center(
                      child: CircleAvatar(
                        radius: 60,
                        backgroundImage: candidate.profilePicUrl != null
                            ? NetworkImage(candidate.profilePicUrl!)
                            : null,
                        child: candidate.profilePicUrl == null
                            ? const Icon(Icons.person, size: 80)
                            : null,
                      ),
                    ),
                    const SizedBox(height: 24),
                    Text(
                      candidate.name,
                      style: const TextStyle(
                        fontSize: 28,
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                    const SizedBox(height: 8),
                    Text(
                      candidate.headline ?? candidate.title,
                      style: const TextStyle(
                        fontSize: 18,
                        color: Colors.blueGrey,
                        fontWeight: FontWeight.w500,
                      ),
                    ),
                    if (candidate.location != null) ...[
                      const SizedBox(height: 8),
                      Row(
                        children: [
                          const Icon(
                            Icons.location_on,
                            size: 16,
                            color: Colors.grey,
                          ),
                          const SizedBox(width: 4),
                          Text(
                            candidate.location!,
                            style: const TextStyle(color: Colors.grey),
                          ),
                        ],
                      ),
                    ],
                    const SizedBox(height: 24),
                    if (candidate.summary != null &&
                        candidate.summary!.isNotEmpty) ...[
                      const Text(
                        'About',
                        style: TextStyle(
                          fontSize: 20,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                      const SizedBox(height: 8),
                      Text(
                        candidate.summary!,
                        style: const TextStyle(fontSize: 16, height: 1.5),
                      ),
                      const SizedBox(height: 24),
                    ],
                    const Text(
                      'Contact Info',
                      style: TextStyle(
                        fontSize: 20,
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                    const SizedBox(height: 8),
                    if (candidate.email != null &&
                        candidate.email!.isNotEmpty) ...[
                      SelectableText(
                        'Email: ${candidate.email}',
                        style: const TextStyle(fontSize: 16),
                      ),
                      const SizedBox(height: 8),
                    ],
                    if (candidate.phone != null &&
                        candidate.phone!.isNotEmpty) ...[
                      SelectableText(
                        'Phone: ${candidate.phone}',
                        style: const TextStyle(fontSize: 16),
                      ),
                      const SizedBox(height: 8),
                    ],
                    if (candidate.companyWebsite != null &&
                        candidate.companyWebsite!.isNotEmpty) ...[
                      SelectableText(
                        'Company Website: ${candidate.companyWebsite}',
                        style: const TextStyle(fontSize: 16),
                      ),
                      const SizedBox(height: 8),
                    ],
                    if (candidate.personalWebsite != null &&
                        candidate.personalWebsite!.isNotEmpty) ...[
                      SelectableText(
                        'Personal Website: ${candidate.personalWebsite}',
                        style: const TextStyle(fontSize: 16),
                      ),
                      const SizedBox(height: 8),
                    ],
                    if (candidate.socialProfileUrl != null &&
                        candidate.socialProfileUrl!.isNotEmpty) ...[
                      SelectableText(
                        'Social Profile: ${candidate.socialProfileUrl}',
                        style: const TextStyle(fontSize: 16),
                      ),
                      const SizedBox(height: 8),
                    ],
                    SelectableText(
                      'LinkedIn: ${candidate.linkedinUrl}',
                      style: const TextStyle(color: Colors.blue),
                    ),
                    const SizedBox(height: 32),
                    SizedBox(
                      width: double.infinity,
                      child: ElevatedButton(
                        style: ElevatedButton.styleFrom(
                          padding: const EdgeInsets.symmetric(vertical: 16),
                        ),
                        onPressed: () {
                          ScaffoldMessenger.of(context).showSnackBar(
                            const SnackBar(
                              content: Text(
                                'Save and export flow will be added next.',
                              ),
                            ),
                          );
                        },
                        child: const Text('Save Candidate'),
                      ),
                    ),
                  ],
                ),
              ),
      ),
    );
  }
}
